


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE OR REPLACE FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    if not exists (select 1 from public.empresas e where e.id = p_empresa_id) then
        raise exception 'Empresa não encontrada.';
    end if;

    if p_ativa = false and exists (
        select 1
          from public.empresa_usuarios eu
         where eu.empresa_id = p_empresa_id
           and eu.user_id = auth.uid()
           and eu.ativo = true
    ) then
        raise exception 'Você não pode bloquear a empresa usada pelo seu próprio acesso atual.';
    end if;

    update public.empresas e
       set ativa = p_ativa,
           updated_at = now()
     where e.id = p_empresa_id;
end;
$$;


ALTER FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") RETURNS "uuid"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_item jsonb;
    v_antigo record;
    v_produto_id uuid;
    v_quantidade numeric(14,3);
    v_custo_unitario numeric(14,2);
    v_estoque_atual numeric(14,3);
    v_custo_atual numeric(14,2);
    v_controla_estoque boolean;
    v_novo_estoque numeric(14,3);
    v_novo_custo numeric(14,2);
    v_total numeric(14,2) := 0;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    perform 1
      from public.compras c
     where c.id = p_compra_id
       and c.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Compra não encontrada ou não pertence a esta empresa.';
    end if;

    if p_itens is null or jsonb_array_length(p_itens) = 0 then
        raise exception 'A compra precisa possuir pelo menos um produto.';
    end if;

    -- Primeiro devolve a compra antiga para o estado anterior do estoque.
    for v_antigo in
        select ci.produto_id, sum(ci.quantidade)::numeric as quantidade
          from public.compra_itens ci
         where ci.compra_id = p_compra_id
           and ci.empresa_id = p_empresa_id
         group by ci.produto_id
    loop
        select p.estoque, coalesce(p.controla_estoque, true)
          into v_estoque_atual, v_controla_estoque
          from public.produtos p
         where p.id = v_antigo.produto_id
           and p.empresa_id = p_empresa_id
         for update;

        if not found then
            raise exception 'Produto da compra não foi encontrado.';
        end if;

        if v_controla_estoque then
            if v_estoque_atual < v_antigo.quantidade then
                raise exception 'Não é possível editar esta compra porque parte do estoque já foi utilizada. Produto: %.',
                    (select p.nome from public.produtos p where p.id = v_antigo.produto_id);
            end if;

            update public.produtos p
               set estoque = p.estoque - v_antigo.quantidade
             where p.id = v_antigo.produto_id
               and p.empresa_id = p_empresa_id;
        end if;
    end loop;

    delete from public.compra_itens ci
     where ci.compra_id = p_compra_id
       and ci.empresa_id = p_empresa_id;

    -- Aplica novamente os itens informados.
    for v_item in
        select item
          from jsonb_array_elements(p_itens) as itens(item)
    loop
        v_produto_id := (v_item ->> 'produto_id')::uuid;
        v_quantidade := (v_item ->> 'quantidade')::numeric;
        v_custo_unitario := (v_item ->> 'custo_unitario')::numeric;

        if v_quantidade <= 0 then
            raise exception 'Quantidade inválida.';
        end if;
        if v_custo_unitario < 0 then
            raise exception 'Custo inválido.';
        end if;

        select p.estoque, p.custo, coalesce(p.controla_estoque, true)
          into v_estoque_atual, v_custo_atual, v_controla_estoque
          from public.produtos p
         where p.id = v_produto_id
           and p.empresa_id = p_empresa_id
         for update;

        if not found then
            raise exception 'Produto não encontrado ou pertencente a outra empresa.';
        end if;

        if v_controla_estoque then
            v_novo_estoque := v_estoque_atual + v_quantidade;
            if v_novo_estoque > 0 then
                v_novo_custo := (
                    (v_custo_atual * v_estoque_atual) +
                    (v_custo_unitario * v_quantidade)
                ) / v_novo_estoque;
            else
                v_novo_custo := v_custo_unitario;
            end if;

            update public.produtos p
               set estoque = v_novo_estoque,
                   custo = v_novo_custo
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        else
            update public.produtos p
               set custo = v_custo_unitario
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        end if;

        insert into public.compra_itens (
            empresa_id,
            compra_id,
            produto_id,
            quantidade,
            custo_unitario,
            total
        ) values (
            p_empresa_id,
            p_compra_id,
            v_produto_id,
            v_quantidade,
            v_custo_unitario,
            v_quantidade * v_custo_unitario
        );

        v_total := v_total + (v_quantidade * v_custo_unitario);
    end loop;

    update public.compras c
       set fornecedor = p_fornecedor,
           data_compra = p_data_compra,
           observacoes = p_observacoes,
           total = v_total
     where c.id = p_compra_id
       and c.empresa_id = p_empresa_id;

    return p_compra_id;
end;
$$;


ALTER FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
    v_empresa_id uuid;
    v_perfil_atual text;
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    if p_perfil not in ('OWNER', 'ADMIN', 'MANAGER', 'SELLER') then
        raise exception 'Perfil inválido.';
    end if;

    select eu.empresa_id, eu.perfil
      into v_empresa_id, v_perfil_atual
      from public.empresa_usuarios eu
     where eu.id = p_vinculo_id;

    if v_empresa_id is null then
        raise exception 'Vínculo de usuário não encontrado.';
    end if;

    -- Nunca deixa a empresa sem pelo menos um OWNER ativo.
    if v_perfil_atual = 'OWNER' and (p_perfil <> 'OWNER' or p_ativo = false) then
        if not exists (
            select 1
              from public.empresa_usuarios eu
             where eu.empresa_id = v_empresa_id
               and eu.id <> p_vinculo_id
               and eu.perfil = 'OWNER'
               and eu.ativo = true
        ) then
            raise exception 'A empresa precisa manter pelo menos um OWNER ativo.';
        end if;
    end if;

    update public.empresa_usuarios eu
       set perfil = p_perfil,
           ativo = p_ativo
     where eu.id = p_vinculo_id;
end;
$$;


ALTER FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $_$
declare
    v_status_original text;
    v_pago_em_original timestamptz;
    v_total_recebido numeric(14,2) := 0;
    v_status_novo text;
    v_cliente_nome text := 'Venda rápida';

    v_item jsonb;
    v_produto_id uuid;
    v_produto_nome text;
    v_quantidade numeric(14,3);
    v_estoque numeric(14,3);
    v_controla_estoque boolean;
    v_custo_produto numeric(14,2);
    v_custo_unitario numeric(14,2);
    v_preco_unitario numeric(14,2);
    v_total_custo_item numeric(14,2);
    v_total_venda_item numeric(14,2);
    v_lucro_item numeric(14,2);
    v_total_custo numeric(14,2) := 0;
    v_total_venda numeric(14,2) := 0;
    v_lucro numeric(14,2) := 0;
    v_old record;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    select v.status_pagamento, v.pago_em
      into v_status_original, v_pago_em_original
      from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Venda não encontrada.';
    end if;

    if p_itens is null or jsonb_array_length(p_itens) = 0 then
        raise exception 'A venda precisa possuir pelo menos um produto.';
    end if;

    select coalesce(sum(r.valor), 0)
      into v_total_recebido
      from public.venda_recebimentos r
     where r.empresa_id = p_empresa_id
       and r.venda_id = p_venda_id;

    if p_cliente_id is null and (v_status_original = 'A_RECEBER' or v_total_recebido > 0) then
        raise exception 'Uma venda fiada ou com recebimentos registrados precisa permanecer vinculada a um cliente.';
    end if;

    if p_cliente_id is not null then
        select c.nome
          into v_cliente_nome
          from public.clientes c
         where c.id = p_cliente_id
           and c.empresa_id = p_empresa_id;

        if not found then
            raise exception 'Cliente não encontrado ou pertencente a outra empresa.';
        end if;
    end if;

    -- Primeiro devolve ao estoque tudo que a venda antiga havia baixado.
    for v_old in
        select vi.produto_id, vi.quantidade, p.controla_estoque
          from public.venda_itens vi
          join public.produtos p
            on p.id = vi.produto_id
           and p.empresa_id = p_empresa_id
         where vi.venda_id = p_venda_id
           and vi.empresa_id = p_empresa_id
         for update of p
    loop
        if v_old.controla_estoque then
            update public.produtos p
               set estoque = p.estoque + v_old.quantidade
             where p.id = v_old.produto_id
               and p.empresa_id = p_empresa_id;
        end if;
    end loop;

    delete from public.venda_itens vi
     where vi.venda_id = p_venda_id
       and vi.empresa_id = p_empresa_id;

    -- Recria os itens usando o novo conteúdo da venda.
    for v_item in
        select item
          from jsonb_array_elements(p_itens) as itens(item)
    loop
        v_produto_id := (v_item ->> 'produto_id')::uuid;
        v_quantidade := (v_item ->> 'quantidade')::numeric;
        v_preco_unitario := (v_item ->> 'preco_unitario')::numeric;

        if v_quantidade is null or v_quantidade <= 0 then
            raise exception 'Quantidade inválida.';
        end if;

        if v_preco_unitario is null or v_preco_unitario < 0 then
            raise exception 'Preço de venda inválido.';
        end if;

        select p.nome, p.estoque, p.custo, p.controla_estoque
          into v_produto_nome, v_estoque, v_custo_produto, v_controla_estoque
          from public.produtos p
         where p.id = v_produto_id
           and p.empresa_id = p_empresa_id
         for update;

        if not found then
            raise exception 'Produto não encontrado ou pertencente a outra empresa.';
        end if;

        v_custo_unitario := coalesce(
            nullif(v_item ->> 'custo_unitario', '')::numeric,
            v_custo_produto
        );

        if v_custo_unitario < 0 then
            raise exception 'Custo inválido.';
        end if;

        if v_controla_estoque and v_estoque < v_quantidade then
            raise exception 'Estoque insuficiente para %. Disponível: %', v_produto_nome, v_estoque;
        end if;

        v_total_custo_item := round(v_quantidade * v_custo_unitario, 2);
        v_total_venda_item := round(v_quantidade * v_preco_unitario, 2);
        v_lucro_item := v_total_venda_item - v_total_custo_item;

        insert into public.venda_itens (
            empresa_id,
            venda_id,
            produto_id,
            produto_nome,
            quantidade,
            custo_unitario,
            preco_unitario,
            total_custo,
            total_venda,
            lucro
        ) values (
            p_empresa_id,
            p_venda_id,
            v_produto_id,
            v_produto_nome,
            v_quantidade,
            v_custo_unitario,
            v_preco_unitario,
            v_total_custo_item,
            v_total_venda_item,
            v_lucro_item
        );

        if v_controla_estoque then
            update public.produtos p
               set estoque = p.estoque - v_quantidade
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        end if;

        v_total_custo := v_total_custo + v_total_custo_item;
        v_total_venda := v_total_venda + v_total_venda_item;
        v_lucro := v_lucro + v_lucro_item;
    end loop;

    if round(v_total_recebido, 2) > round(v_total_venda, 2) then
        raise exception 'O novo total da venda não pode ser menor que o valor já recebido (R$ %).', to_char(v_total_recebido, 'FM999999990D00');
    end if;

    if v_status_original = 'PAGO' and v_total_recebido = 0 then
        -- Venda originalmente paga no ato: continua paga mesmo após a edição.
        v_status_novo := 'PAGO';
    elsif round(v_total_recebido, 2) >= round(v_total_venda, 2) then
        v_status_novo := 'PAGO';
    else
        v_status_novo := 'A_RECEBER';
    end if;

    if p_cliente_id is null and v_status_novo = 'A_RECEBER' then
        raise exception 'Venda rápida não pode ficar a receber.';
    end if;

    update public.vendas v
       set cliente_id = p_cliente_id,
           cliente_nome = v_cliente_nome,
           data_venda = p_data_venda::timestamp at time zone 'America/Sao_Paulo',
           total_custo = v_total_custo,
           total_venda = v_total_venda,
           lucro = v_lucro,
           observacoes = nullif(trim(coalesce(p_observacoes, '')), ''),
           status_pagamento = v_status_novo,
           pago_em = case
               when v_status_novo = 'PAGO' then coalesce(v_pago_em_original, now())
               else null
           end
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id;
end;
$_$;


ALTER FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_status_desejado text;
    v_total_venda numeric(14,2);
    v_total_recebido numeric(14,2) := 0;
    v_status_calculado text;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    v_status_desejado := upper(trim(coalesce(p_status_pagamento, '')));

    if v_status_desejado not in ('PAGO', 'A_RECEBER') then
        raise exception 'Status de pagamento inválido.';
    end if;

    if p_cliente_id is null and v_status_desejado = 'A_RECEBER' then
        raise exception 'Venda rápida não pode ficar a receber.';
    end if;

    -- Atualiza cliente, data, itens, estoque e valores usando a rotina já existente.
    -- Como esta chamada ocorre dentro desta função, tudo faz parte da mesma transação.
    perform public.atualizar_venda(
        p_empresa_id,
        p_venda_id,
        p_cliente_id,
        p_data_venda,
        p_observacoes,
        p_itens
    );

    select v.total_venda
      into v_total_venda
      from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Venda não encontrada.';
    end if;

    select coalesce(sum(r.valor), 0)
      into v_total_recebido
      from public.venda_recebimentos r
     where r.empresa_id = p_empresa_id
       and r.venda_id = p_venda_id;

    if round(v_total_recebido, 2) > 0 then
        v_status_calculado := case
            when round(v_total_recebido, 2) >= round(v_total_venda, 2) then 'PAGO'
            else 'A_RECEBER'
        end;

        if v_status_desejado <> v_status_calculado then
            raise exception 'Esta venda possui abatimentos registrados. O status deve seguir o saldo atual (%).',
                case when v_status_calculado = 'PAGO' then 'Pago' else 'A receber' end;
        end if;
    else
        v_status_calculado := v_status_desejado;
    end if;

    update public.vendas v
       set status_pagamento = v_status_calculado,
           pago_em = case
               when v_status_calculado = 'PAGO' then coalesce(v.pago_em, now())
               else null
           end
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id;
end;
$$;


ALTER FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();
  update public.pedidos_cliente
  set conversao_por = null, conversao_em = null, updated_at = now()
  where id = p_pedido_id
    and empresa_id = v_empresa_id
    and status = 'PENDENTE'
    and conversao_por = auth.uid();
end;
$$;


ALTER FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."criar_grupo_produto"("p_nome" "text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
  v_perfil text;
  v_nome text := btrim(coalesce(p_nome, ''));
  v_id uuid;
  v_ordem integer;
begin
  select to_jsonb(ctx)
    into v_contexto
    from public.obter_contexto_usuario() ctx
   limit 1;

  v_empresa_id := nullif(v_contexto ->> 'empresa_id', '')::uuid;
  v_perfil := upper(coalesce(v_contexto ->> 'perfil', ''));

  if v_empresa_id is null or v_perfil <> 'OWNER' then
    raise exception 'Somente o proprietário (OWNER) pode criar grupos de produtos.' using errcode = '42501';
  end if;

  if v_nome = '' then
    raise exception 'Informe o nome do grupo.' using errcode = '22023';
  end if;

  if exists (
    select 1 from public.produto_grupos g
     where g.empresa_id = v_empresa_id
       and lower(btrim(g.nome)) = lower(v_nome)
  ) then
    raise exception 'Já existe um grupo com este nome.' using errcode = '23505';
  end if;

  select coalesce(max(g.ordem), 0) + 1
    into v_ordem
    from public.produto_grupos g
   where g.empresa_id = v_empresa_id;

  insert into public.produto_grupos (empresa_id, nome, ordem)
  values (v_empresa_id, v_nome, v_ordem)
  returning id into v_id;

  return v_id;
end;
$$;


ALTER FUNCTION "public"."criar_grupo_produto"("p_nome" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_cliente public.clientes%rowtype;
  v_pedido_id uuid;
  v_item jsonb;
  v_produto_id uuid;
  v_quantidade numeric;
  v_produto public.produtos%rowtype;
  v_tipo text;
  v_taxa numeric;
  v_preco_fixo numeric;
  v_preco numeric;
  v_total numeric := 0;
  v_seen uuid[] := array[]::uuid[];
begin
  if p_itens is null or jsonb_typeof(p_itens) <> 'array' or jsonb_array_length(p_itens) = 0 then
    raise exception 'Adicione pelo menos um produto ao pedido.';
  end if;

  if jsonb_array_length(p_itens) > 100 then
    raise exception 'O pedido excede o limite de itens.';
  end if;

  select c.* into v_cliente
  from public.clientes c
  join public.empresas e on e.id = c.empresa_id
  where c.pedido_token = p_token
    and c.ativo = true
    and e.ativa = true
  limit 1;

  if v_cliente.id is null then
    raise exception 'Este link de pedido não é válido ou não está mais disponível.';
  end if;

  insert into public.pedidos_cliente (empresa_id, cliente_id, observacoes)
  values (v_cliente.empresa_id, v_cliente.id, nullif(trim(coalesce(p_observacoes, '')), ''))
  returning id into v_pedido_id;

  for v_item in select value from jsonb_array_elements(p_itens)
  loop
    begin
      v_produto_id := (v_item->>'produto_id')::uuid;
      v_quantidade := (v_item->>'quantidade')::numeric;
    exception when others then
      raise exception 'Item de pedido inválido.';
    end;

    if v_quantidade is null or v_quantidade <= 0 or v_quantidade > 99999 then
      raise exception 'Quantidade inválida.';
    end if;

    if v_produto_id = any(v_seen) then
      raise exception 'O mesmo produto foi enviado mais de uma vez.';
    end if;
    v_seen := array_append(v_seen, v_produto_id);

    select p.* into v_produto
    from public.produtos p
    where p.id = v_produto_id
      and p.empresa_id = v_cliente.empresa_id
      and p.ativo = true;

    if v_produto.id is null then
      raise exception 'Um dos produtos não está mais disponível.';
    end if;

    if exists (
      select 1
      from public.cliente_produtos_ocultos cpo
      where cpo.empresa_id = v_cliente.empresa_id
        and cpo.cliente_id = v_cliente.id
        and cpo.produto_id = v_produto.id
    ) then
      raise exception 'Este produto não está disponível para este cliente.';
    end if;

    select cpp.tipo, cpp.taxa, cpp.preco_fixo
      into v_tipo, v_taxa, v_preco_fixo
    from public.cliente_produto_preco cpp
    where cpp.empresa_id = v_cliente.empresa_id
      and cpp.cliente_id = v_cliente.id
      and cpp.produto_id = v_produto.id
    limit 1;

    v_preco := case
      when v_tipo = 'preco_fixo' and v_preco_fixo is not null then v_preco_fixo
      when v_tipo is not null and v_tipo <> 'preco_fixo' and v_taxa is not null then v_produto.custo * (1 + v_taxa / 100.0)
      when coalesce(v_cliente.taxa_padrao, 0) = 0 then v_produto.preco_padrao
      else v_produto.custo * (1 + v_cliente.taxa_padrao / 100.0)
    end;
    v_preco := round(coalesce(v_preco, 0)::numeric, 2);

    insert into public.pedido_cliente_itens (
      pedido_id, empresa_id, produto_id, produto_nome, quantidade, preco_unitario, total
    ) values (
      v_pedido_id, v_cliente.empresa_id, v_produto.id, v_produto.nome,
      v_quantidade, v_preco, round((v_preco * v_quantidade)::numeric, 2)
    );

    v_total := v_total + (v_preco * v_quantidade);
  end loop;

  update public.pedidos_cliente
  set total = round(v_total::numeric, 2), updated_at = now()
  where id = v_pedido_id;

  return v_pedido_id;
end;
$$;


ALTER FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text" DEFAULT NULL::"text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_id uuid;
begin
  if not public.usuario_tem_acesso_empresa_v150(p_empresa_id) then
    raise exception 'Usuário sem acesso a esta empresa.';
  end if;

  if p_valor is null or p_valor <= 0 then
    raise exception 'O valor deve ser maior que zero.';
  end if;

  if not exists (
    select 1
    from public.clientes c
    where c.id = p_cliente_id
      and c.empresa_id = p_empresa_id
      and c.ativo = true
  ) then
    raise exception 'Cliente inválido ou inativo para esta empresa.';
  end if;

  insert into public.recebiveis_manuais (
    empresa_id,
    cliente_id,
    valor_original,
    data_lancamento,
    observacoes
  )
  values (
    p_empresa_id,
    p_cliente_id,
    round(p_valor, 2),
    coalesce(p_data_lancamento, current_date),
    nullif(trim(coalesce(p_observacoes, '')), '')
  )
  returning id into v_id;

  return v_id;
end;
$$;


ALTER FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."diagnosticar_meu_acesso"() RETURNS TABLE("user_id" "uuid", "status" "text", "empresa_id" "uuid", "empresa" "text", "perfil" "text", "empresa_ativa" boolean)
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    select
        auth.uid(),
        c.status,
        c.empresa_id,
        c.nome_fantasia,
        c.perfil,
        c.ativa
      from public.obter_contexto_usuario() c;
$$;


ALTER FUNCTION "public"."diagnosticar_meu_acesso"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."empresa_usuario_atual_pedidos"() RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
  v_status text;
begin
  select to_jsonb(public.obter_contexto_usuario())
    into v_contexto
  limit 1;

  v_status := v_contexto->>'status';
  v_empresa_id := nullif(v_contexto->>'empresa_id', '')::uuid;

  if coalesce(v_status, '') <> 'OK' or v_empresa_id is null then
    raise exception 'Usuário sem acesso a uma empresa ativa.';
  end if;

  return v_empresa_id;
end;
$$;


ALTER FUNCTION "public"."empresa_usuario_atual_pedidos"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_antigo record;
    v_estoque_atual numeric(14,3);
    v_controla_estoque boolean;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    perform 1
      from public.compras c
     where c.id = p_compra_id
       and c.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Compra não encontrada ou não pertence a esta empresa.';
    end if;

    for v_antigo in
        select ci.produto_id, sum(ci.quantidade)::numeric as quantidade
          from public.compra_itens ci
         where ci.compra_id = p_compra_id
           and ci.empresa_id = p_empresa_id
         group by ci.produto_id
    loop
        select p.estoque, coalesce(p.controla_estoque, true)
          into v_estoque_atual, v_controla_estoque
          from public.produtos p
         where p.id = v_antigo.produto_id
           and p.empresa_id = p_empresa_id
         for update;

        if not found then
            raise exception 'Produto da compra não foi encontrado.';
        end if;

        if v_controla_estoque then
            if v_estoque_atual < v_antigo.quantidade then
                raise exception 'Não é possível excluir esta compra porque parte do estoque já foi utilizada. Produto: %.',
                    (select p.nome from public.produtos p where p.id = v_antigo.produto_id);
            end if;

            update public.produtos p
               set estoque = p.estoque - v_antigo.quantidade
             where p.id = v_antigo.produto_id
               and p.empresa_id = p_empresa_id;
        end if;
    end loop;

    delete from public.compra_itens ci
     where ci.compra_id = p_compra_id
       and ci.empresa_id = p_empresa_id;

    delete from public.compras c
     where c.id = p_compra_id
       and c.empresa_id = p_empresa_id;
end;
$$;


ALTER FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    if not exists (select 1 from public.empresas e where e.id = p_empresa_id) then
        raise exception 'Empresa não encontrada.';
    end if;

    if exists (
        select 1
          from public.empresas e
         where e.id = p_empresa_id
           and e.ativa = true
    ) then
        raise exception 'Bloqueie a empresa antes de excluí-la.';
    end if;

    if exists (
        select 1
          from public.empresa_usuarios eu
         where eu.empresa_id = p_empresa_id
           and eu.user_id = auth.uid()
           and eu.ativo = true
    ) then
        raise exception 'Você não pode excluir a empresa usada pelo seu próprio acesso.';
    end if;

    delete from public.venda_itens where empresa_id = p_empresa_id;
    delete from public.vendas where empresa_id = p_empresa_id;
    delete from public.compra_itens where empresa_id = p_empresa_id;
    delete from public.compras where empresa_id = p_empresa_id;
    delete from public.cliente_produto_preco where empresa_id = p_empresa_id;
    delete from public.clientes where empresa_id = p_empresa_id;
    delete from public.produtos where empresa_id = p_empresa_id;
    delete from public.empresa_usuarios where empresa_id = p_empresa_id;
    delete from public.empresas where id = p_empresa_id;
end;
$$;


ALTER FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
  v_perfil text;
begin
  select to_jsonb(ctx)
    into v_contexto
    from public.obter_contexto_usuario() ctx
   limit 1;

  v_empresa_id := nullif(v_contexto ->> 'empresa_id', '')::uuid;
  v_perfil := upper(coalesce(v_contexto ->> 'perfil', ''));

  if v_empresa_id is null or v_perfil <> 'OWNER' then
    raise exception 'Somente o proprietário (OWNER) pode excluir grupos de produtos.' using errcode = '42501';
  end if;

  delete from public.produto_grupos g
   where g.id = p_grupo_id
     and g.empresa_id = v_empresa_id;

  if not found then
    raise exception 'Grupo de produtos não encontrado.' using errcode = 'P0002';
  end if;
end;
$$;


ALTER FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  delete from public.pedidos_cliente
  where id = p_pedido_id
    and empresa_id = v_empresa_id
    and status = 'RECUSADO';

  if not found then
    raise exception 'Somente solicitações recusadas podem ser apagadas.';
  end if;
end;
$$;


ALTER FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_old record;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    perform 1
      from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Venda não encontrada.';
    end if;

    for v_old in
        select vi.produto_id, vi.quantidade, p.controla_estoque
          from public.venda_itens vi
          join public.produtos p
            on p.id = vi.produto_id
           and p.empresa_id = p_empresa_id
         where vi.venda_id = p_venda_id
           and vi.empresa_id = p_empresa_id
         for update of p
    loop
        if v_old.controla_estoque then
            update public.produtos p
               set estoque = p.estoque + v_old.quantidade
             where p.id = v_old.produto_id
               and p.empresa_id = p_empresa_id;
        end if;
    end loop;

    -- venda_itens e venda_recebimentos são removidos por ON DELETE CASCADE.
    delete from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id;
end;
$$;


ALTER FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  requested_role text;
  linked_student_id uuid;
  linked_owner_id uuid;
begin
  requested_role := case when new.raw_user_meta_data->>'role' = 'student' then 'student' else 'owner' end;
  linked_student_id := nullif(new.raw_user_meta_data->>'student_id', '')::uuid;
  linked_owner_id := nullif(new.raw_user_meta_data->>'owner_id', '')::uuid;

  insert into public.profiles(id, full_name, role, student_id, owner_id)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1)),
    requested_role,
    case when requested_role = 'student' then linked_student_id else null end,
    case when requested_role = 'student' then linked_owner_id else null end
  )
  on conflict (id) do update set
    full_name = excluded.full_name,
    role = excluded.role,
    student_id = excluded.student_id,
    owner_id = excluded.owner_id,
    updated_at = now();

  return new;
end;
$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
  v_ok boolean := false;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  update public.pedidos_cliente
  set conversao_por = auth.uid(), conversao_em = now(), visualizado_em = coalesce(visualizado_em, now()), updated_at = now()
  where id = p_pedido_id
    and empresa_id = v_empresa_id
    and status = 'PENDENTE'
    and (
      conversao_por is null
      or conversao_por = auth.uid()
      or conversao_em < now() - interval '15 minutes'
    );

  v_ok := found;
  return v_ok;
end;
$$;


ALTER FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."listar_empresas_plataforma"() RETURNS TABLE("id" "uuid", "slug" "text", "nome_fantasia" "text", "razao_social" "text", "cpf_cnpj" "text", "ativa" boolean, "created_at" timestamp with time zone, "email_owner" "text", "total_usuarios" bigint, "total_clientes" bigint, "total_produtos" bigint, "total_vendas" bigint)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $$
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    return query
    select
        e.id,
        e.slug,
        e.nome_fantasia,
        e.razao_social,
        e.cpf_cnpj,
        e.ativa,
        e.created_at,
        owner_data.email::text as email_owner,
        (select count(*) from public.empresa_usuarios eu where eu.empresa_id = e.id and eu.ativo = true) as total_usuarios,
        (select count(*) from public.clientes c where c.empresa_id = e.id) as total_clientes,
        (select count(*) from public.produtos p where p.empresa_id = e.id) as total_produtos,
        (select count(*) from public.vendas v where v.empresa_id = e.id) as total_vendas
    from public.empresas e
    left join lateral (
        select u.email
          from public.empresa_usuarios eu
          join auth.users u on u.id = eu.user_id
         where eu.empresa_id = e.id
           and eu.perfil = 'OWNER'
           and eu.ativo = true
         order by eu.created_at asc
         limit 1
    ) owner_data on true
    order by e.created_at desc;
end;
$$;


ALTER FUNCTION "public"."listar_empresas_plataforma"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."listar_grupos_produto"() RETURNS TABLE("id" "uuid", "nome" "text", "ordem" integer, "created_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
begin
  select to_jsonb(ctx)
    into v_contexto
    from public.obter_contexto_usuario() ctx
   limit 1;

  v_empresa_id := nullif(v_contexto ->> 'empresa_id', '')::uuid;
  if v_empresa_id is null then
    return;
  end if;

  return query
  select g.id, g.nome, g.ordem, g.created_at
    from public.produto_grupos g
   where g.empresa_id = v_empresa_id
   order by g.ordem asc, lower(g.nome) asc, g.created_at asc;
end;
$$;


ALTER FUNCTION "public"."listar_grupos_produto"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."listar_pedidos_cliente"() RETURNS TABLE("id" "uuid", "cliente_id" "uuid", "cliente_nome" "text", "status" "text", "visualizado_em" timestamp with time zone, "observacoes" "text", "total" numeric, "venda_id" "uuid", "created_at" timestamp with time zone, "itens" "jsonb")
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  return query
  select
    pc.id,
    pc.cliente_id,
    c.nome::text,
    pc.status,
    pc.visualizado_em,
    pc.observacoes,
    pc.total,
    pc.venda_id,
    pc.created_at,
    coalesce(jsonb_agg(jsonb_build_object(
      'id', pci.id,
      'produto_id', pci.produto_id,
      'produto_nome', pci.produto_nome,
      'quantidade', pci.quantidade,
      'preco_unitario', pci.preco_unitario,
      'total', pci.total
    ) order by pci.created_at) filter (where pci.id is not null), '[]'::jsonb)
  from public.pedidos_cliente pc
  join public.clientes c on c.id = pc.cliente_id
  left join public.pedido_cliente_itens pci on pci.pedido_id = pc.id
  where pc.empresa_id = v_empresa_id
  group by pc.id, c.nome
  order by (pc.status = 'PENDENTE' and pc.visualizado_em is null) desc, pc.created_at desc;
end;
$$;


ALTER FUNCTION "public"."listar_pedidos_cliente"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") RETURNS TABLE("produto_id" "uuid")
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  if not exists (
    select 1
    from public.clientes c
    where c.id = p_cliente_id
      and c.empresa_id = v_empresa_id
  ) then
    raise exception 'Cliente não encontrado para esta empresa.';
  end if;

  return query
  select cpo.produto_id
  from public.cliente_produtos_ocultos cpo
  where cpo.empresa_id = v_empresa_id
    and cpo.cliente_id = p_cliente_id
  order by cpo.created_at, cpo.produto_id;
end;
$$;


ALTER FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") RETURNS TABLE("vinculo_id" "uuid", "user_id" "uuid", "email" "text", "perfil" "text", "ativo" boolean, "created_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $$
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    return query
    select
        eu.id as vinculo_id,
        eu.user_id,
        u.email::text,
        eu.perfil,
        eu.ativo,
        eu.created_at
      from public.empresa_usuarios eu
      join auth.users u on u.id = eu.user_id
     where eu.empresa_id = p_empresa_id
     order by
        case eu.perfil
            when 'OWNER' then 1
            when 'ADMIN' then 2
            when 'MANAGER' then 3
            else 4
        end,
        u.email;
end;
$$;


ALTER FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  if not exists (
    select 1 from public.vendas v
    where v.id = p_venda_id and v.empresa_id = v_empresa_id
  ) then
    raise exception 'Venda não encontrada para esta empresa.';
  end if;

  update public.pedidos_cliente
  set status = 'CONVERTIDO', venda_id = p_venda_id, visualizado_em = coalesce(visualizado_em, now()),
      conversao_por = null, conversao_em = null, updated_at = now()
  where id = p_pedido_id
    and empresa_id = v_empresa_id
    and status = 'PENDENTE'
    and conversao_por = auth.uid();

  if not found then
    raise exception 'Este pedido não pode mais ser convertido ou está sendo usado por outro usuário.';
  end if;
end;
$$;


ALTER FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();
  update public.pedidos_cliente
  set visualizado_em = coalesce(visualizado_em, now()), updated_at = now()
  where id = p_pedido_id and empresa_id = v_empresa_id;
  if not found then raise exception 'Pedido não encontrado.'; end if;
end;
$$;


ALTER FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_total_venda numeric(14,2);
    v_status text;
    v_recebido numeric(14,2);
    v_saldo numeric(14,2);
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    select v.total_venda, v.status_pagamento
      into v_total_venda, v_status
      from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Venda não encontrada.';
    end if;

    if v_status = 'PAGO' then
        raise exception 'Esta venda já está paga.';
    end if;

    select coalesce(sum(r.valor), 0)
      into v_recebido
      from public.venda_recebimentos r
     where r.empresa_id = p_empresa_id
       and r.venda_id = p_venda_id;

    v_saldo := round(v_total_venda - v_recebido, 2);

    if v_saldo <= 0 then
        update public.vendas v
           set status_pagamento = 'PAGO',
               pago_em = coalesce(v.pago_em, now())
         where v.id = p_venda_id
           and v.empresa_id = p_empresa_id;
        return;
    end if;

    perform public.registrar_recebimento_venda(
        p_empresa_id,
        p_venda_id,
        v_saldo,
        current_date,
        'Quitação do saldo'
    );
end;
$$;


ALTER FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_cliente public.clientes%rowtype;
  v_empresa public.empresas%rowtype;
  v_produtos jsonb;
begin
  select c.* into v_cliente
  from public.clientes c
  join public.empresas e on e.id = c.empresa_id
  where c.pedido_token = p_token
    and c.ativo = true
    and e.ativa = true
  limit 1;

  if v_cliente.id is null then
    return null;
  end if;

  select * into v_empresa
  from public.empresas
  where id = v_cliente.empresa_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id,
    'nome', p.nome,
    'marca', p.marca,
    'codigo', p.codigo,
    'preco', round((case
      when cpp.tipo = 'preco_fixo' and cpp.preco_fixo is not null then cpp.preco_fixo
      when cpp.tipo <> 'preco_fixo' and cpp.taxa is not null then p.custo * (1 + cpp.taxa / 100.0)
      when coalesce(v_cliente.taxa_padrao, 0) = 0 then p.preco_padrao
      else p.custo * (1 + v_cliente.taxa_padrao / 100.0)
    end)::numeric, 2),
    'fotos', coalesce((
      select jsonb_agg(pf.url order by pf.ordem, pf.created_at)
      from public.produto_fotos pf
      where pf.empresa_id = v_cliente.empresa_id
        and pf.produto_id = p.id
    ), '[]'::jsonb)
  ) order by p.nome), '[]'::jsonb)
  into v_produtos
  from public.produtos p
  left join public.cliente_produto_preco cpp
    on cpp.empresa_id = v_cliente.empresa_id
   and cpp.cliente_id = v_cliente.id
   and cpp.produto_id = p.id
  where p.empresa_id = v_cliente.empresa_id
    and p.ativo = true
    and not exists (
      select 1
      from public.cliente_produtos_ocultos cpo
      where cpo.empresa_id = v_cliente.empresa_id
        and cpo.cliente_id = v_cliente.id
        and cpo.produto_id = p.id
    );

  return jsonb_build_object(
    'empresa_nome', v_empresa.nome_fantasia,
    'logo_url', v_empresa.logo_url,
    'cor_primaria', v_empresa.cor_primaria,
    'cor_secundaria', v_empresa.cor_secundaria,
    'cliente_nome', v_cliente.nome,
    'produtos', v_produtos
  );
end;
$$;


ALTER FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."obter_contexto_usuario"() RETURNS TABLE("status" "text", "empresa_id" "uuid", "perfil" "text", "slug" "text", "nome_fantasia" "text", "razao_social" "text", "cpf_cnpj" "text", "telefone" "text", "email" "text", "endereco" "text", "cidade" "text", "logo_url" "text", "cor_primaria" "text", "cor_secundaria" "text", "ativa" boolean, "created_at" timestamp with time zone)
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
    v_user_id uuid := auth.uid();
    v_empresa_id uuid;
    v_perfil text;
    v_usuario_ativo boolean;
    v_empresa public.empresas%rowtype;
    v_status text;
begin
    if v_user_id is null then
        return query
        select
            'NAO_AUTENTICADO'::text,
            null::uuid,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::boolean,
            null::timestamptz;
        return;
    end if;

    select eu.empresa_id, eu.perfil, eu.ativo
      into v_empresa_id, v_perfil, v_usuario_ativo
      from public.empresa_usuarios eu
     where eu.user_id = v_user_id
     order by eu.ativo desc, eu.created_at asc
     limit 1;

    if v_empresa_id is null then
        return query
        select
            'SEM_EMPRESA'::text,
            null::uuid,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::boolean,
            null::timestamptz;
        return;
    end if;

    select e.*
      into v_empresa
      from public.empresas e
     where e.id = v_empresa_id;

    if not found then
        return query
        select
            'SEM_EMPRESA'::text,
            null::uuid,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::text,
            null::boolean,
            null::timestamptz;
        return;
    end if;

    if coalesce(v_usuario_ativo, false) = false then
        v_status := 'USUARIO_BLOQUEADO';
    elsif coalesce(v_empresa.ativa, false) = false then
        v_status := 'EMPRESA_BLOQUEADA';
    else
        v_status := 'OK';
    end if;

    return query
    select
        v_status,
        v_empresa.id,
        v_perfil,
        v_empresa.slug,
        v_empresa.nome_fantasia,
        v_empresa.razao_social,
        v_empresa.cpf_cnpj,
        v_empresa.telefone,
        v_empresa.email,
        v_empresa.endereco,
        v_empresa.cidade,
        v_empresa.logo_url,
        v_empresa.cor_primaria,
        v_empresa.cor_secundaria,
        v_empresa.ativa,
        v_empresa.created_at;
end;
$$;


ALTER FUNCTION "public"."obter_contexto_usuario"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") RETURNS TABLE("produto_id" "uuid", "grupo_id" "uuid", "grupo_nome" "text", "grupo_ordem" integer)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_empresa_id uuid;
begin
  select c.empresa_id
    into v_empresa_id
    from public.clientes c
   where c.pedido_token = p_token
     and c.ativo = true
   limit 1;

  if v_empresa_id is null then
    return;
  end if;

  return query
  select p.id,
         p.grupo_id,
         g.nome,
         g.ordem
    from public.produtos p
    left join public.produto_grupos g
      on g.id = p.grupo_id
     and g.empresa_id = p.empresa_id
   where p.empresa_id = v_empresa_id
     and p.ativo = true;
end;
$$;


ALTER FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_cliente public.clientes%rowtype;
  v_pedido public.pedidos_cliente%rowtype;
  v_venda public.vendas%rowtype;
  v_itens jsonb;
  v_total numeric;
  v_created_at timestamptz;
begin
  select c.* into v_cliente
  from public.clientes c
  join public.empresas e on e.id = c.empresa_id
  where c.pedido_token = p_token
    and c.ativo = true
    and e.ativa = true
  limit 1;

  if v_cliente.id is null then
    return null;
  end if;

  -- Primeiro prioriza a solicitação pendente mais recente.
  select pc.* into v_pedido
  from public.pedidos_cliente pc
  where pc.empresa_id = v_cliente.empresa_id
    and pc.cliente_id = v_cliente.id
    and pc.status = 'PENDENTE'
  order by pc.created_at desc
  limit 1;

  -- Se não há pendente, mostra somente a última aprovada/convertida.
  if v_pedido.id is null then
    select pc.* into v_pedido
    from public.pedidos_cliente pc
    where pc.empresa_id = v_cliente.empresa_id
      and pc.cliente_id = v_cliente.id
      and pc.status = 'CONVERTIDO'
    order by pc.created_at desc
    limit 1;
  end if;

  if v_pedido.id is null then
    return null;
  end if;

  if v_pedido.status = 'CONVERTIDO' and v_pedido.venda_id is not null then
    select v.* into v_venda
    from public.vendas v
    where v.id = v_pedido.venda_id
      and v.empresa_id = v_cliente.empresa_id
      and v.cliente_id = v_cliente.id
    limit 1;

    if v_venda.id is not null then
      select coalesce(jsonb_agg(jsonb_build_object(
        'produto_nome', vi.produto_nome,
        'quantidade', vi.quantidade,
        'preco_unitario', vi.preco_unitario,
        'total', vi.total_venda
      ) order by vi.id), '[]'::jsonb)
      into v_itens
      from public.venda_itens vi
      where vi.venda_id = v_venda.id;

      v_total := coalesce(v_venda.total_venda, 0);
      v_created_at := coalesce(v_venda.created_at, v_pedido.created_at);
    end if;
  end if;

  -- Fallback e pedidos ainda pendentes usam a solicitação original.
  if v_itens is null then
    select coalesce(jsonb_agg(jsonb_build_object(
      'produto_nome', pci.produto_nome,
      'quantidade', pci.quantidade,
      'preco_unitario', pci.preco_unitario,
      'total', pci.total
    ) order by pci.created_at), '[]'::jsonb)
    into v_itens
    from public.pedido_cliente_itens pci
    where pci.pedido_id = v_pedido.id;

    v_total := coalesce(v_pedido.total, 0);
    v_created_at := v_pedido.created_at;
  end if;

  return jsonb_build_object(
    'id', v_pedido.id,
    'status', v_pedido.status,
    'total', v_total,
    'created_at', v_created_at,
    'itens', v_itens
  );
end;
$$;


ALTER FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."provisionar_empresa"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $$
declare
    v_user_id uuid;
    v_empresa_id uuid;
    v_slug text;
begin
    select u.id
      into v_user_id
      from auth.users u
     where lower(u.email) = lower(trim(p_email_admin))
     limit 1;

    if v_user_id is null then
        raise exception 'Usuário % não encontrado em Authentication > Users.', p_email_admin;
    end if;

    v_slug := lower(regexp_replace(trim(p_nome_fantasia), '[^a-zA-Z0-9]+', '-', 'g'));
    v_slug := trim(both '-' from v_slug);
    if v_slug = '' then
        v_slug := 'empresa';
    end if;
    v_slug := v_slug || '-' || substr(gen_random_uuid()::text, 1, 6);

    insert into public.empresas (
        slug,
        nome_fantasia,
        razao_social,
        cpf_cnpj
    )
    values (
        v_slug,
        trim(p_nome_fantasia),
        nullif(trim(p_razao_social), ''),
        nullif(trim(p_cpf_cnpj), '')
    )
    returning id into v_empresa_id;

    insert into public.empresa_usuarios (
        empresa_id,
        user_id,
        perfil,
        ativo
    )
    values (
        v_empresa_id,
        v_user_id,
        'OWNER',
        true
    );

    return v_empresa_id;
end;
$$;


ALTER FUNCTION "public"."provisionar_empresa"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text" DEFAULT '#f59e0b'::"text", "p_cor_secundaria" "text" DEFAULT '#101827'::"text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $_$
declare
    v_user_id uuid;
    v_empresa_id uuid;
    v_slug text;
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    if nullif(trim(p_nome_fantasia), '') is null then
        raise exception 'Informe o nome fantasia.';
    end if;

    if nullif(trim(p_email_admin), '') is null then
        raise exception 'Informe o e-mail do administrador.';
    end if;

    if p_cor_primaria !~ '^#[0-9A-Fa-f]{6}$' then
        raise exception 'Cor principal inválida.';
    end if;

    if p_cor_secundaria !~ '^#[0-9A-Fa-f]{6}$' then
        raise exception 'Cor secundária inválida.';
    end if;

    select u.id
      into v_user_id
      from auth.users u
     where lower(u.email) = lower(trim(p_email_admin))
     limit 1;

    if v_user_id is null then
        raise exception 'Usuário % não encontrado em Authentication > Users. Crie o usuário primeiro no Supabase Auth.', p_email_admin;
    end if;

    if exists (
        select 1
          from public.empresa_usuarios eu
         where eu.user_id = v_user_id
           and eu.ativo = true
    ) then
        raise exception 'Este usuário já está vinculado a uma empresa ativa.';
    end if;

    v_slug := lower(regexp_replace(trim(p_nome_fantasia), '[^a-zA-Z0-9]+', '-', 'g'));
    v_slug := trim(both '-' from v_slug);
    if v_slug = '' then
        v_slug := 'empresa';
    end if;
    v_slug := v_slug || '-' || substr(gen_random_uuid()::text, 1, 6);

    insert into public.empresas (
        slug,
        nome_fantasia,
        razao_social,
        cpf_cnpj,
        cor_primaria,
        cor_secundaria,
        ativa
    ) values (
        v_slug,
        trim(p_nome_fantasia),
        nullif(trim(p_razao_social), ''),
        nullif(trim(p_cpf_cnpj), ''),
        p_cor_primaria,
        p_cor_secundaria,
        true
    )
    returning empresas.id into v_empresa_id;

    insert into public.empresa_usuarios (
        empresa_id,
        user_id,
        perfil,
        ativo
    ) values (
        v_empresa_id,
        v_user_id,
        'OWNER',
        true
    );

    return v_empresa_id;
end;
$_$;


ALTER FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text", "p_cor_secundaria" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_recebivel public.recebiveis_manuais%rowtype;
  v_recebido numeric(14,2);
  v_saldo numeric(14,2);
begin
  if not public.usuario_tem_acesso_empresa_v150(p_empresa_id) then
    raise exception 'Usuário sem acesso a esta empresa.';
  end if;

  select * into v_recebivel
  from public.recebiveis_manuais
  where id = p_recebivel_id
    and empresa_id = p_empresa_id
  for update;

  if v_recebivel.id is null then
    raise exception 'Lançamento manual não encontrado.';
  end if;

  if v_recebivel.status <> 'ABERTO' then
    return;
  end if;

  select coalesce(sum(p.valor), 0)
    into v_recebido
  from public.recebivel_manual_pagamentos p
  where p.recebivel_id = p_recebivel_id;

  v_saldo := greatest(0, v_recebivel.valor_original - v_recebido);

  if v_saldo > 0 then
    insert into public.recebivel_manual_pagamentos (
      empresa_id,
      recebivel_id,
      valor,
      data_recebimento,
      observacoes
    ) values (
      p_empresa_id,
      p_recebivel_id,
      v_saldo,
      current_date,
      'Quitação do saldo'
    );
  end if;

  update public.recebiveis_manuais
  set status = 'QUITADO',
      quitado_em = now()
  where id = p_recebivel_id;
end;
$$;


ALTER FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();
  update public.pedidos_cliente
  set status = 'RECUSADO', visualizado_em = coalesce(visualizado_em, now()),
      conversao_por = null, conversao_em = null, updated_at = now()
  where id = p_pedido_id
    and empresa_id = v_empresa_id
    and status = 'PENDENTE'
    and (conversao_por is null or conversao_por = auth.uid() or conversao_em < now() - interval '15 minutes');
  if not found then raise exception 'Este pedido não está mais pendente ou está sendo convertido por outro usuário.'; end if;
end;
$$;


ALTER FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
  v_token uuid := gen_random_uuid();
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  update public.clientes
  set pedido_token = v_token
  where id = p_cliente_id
    and empresa_id = v_empresa_id;

  if not found then
    raise exception 'Cliente não encontrado.';
  end if;

  return v_token;
end;
$$;


ALTER FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") RETURNS "uuid"
    LANGUAGE "plpgsql"
    AS $$
declare
    v_compra_id uuid;
    v_item jsonb;
    v_produto_id uuid;
    v_quantidade numeric(14,3);
    v_custo_unitario numeric(14,2);
    v_estoque_atual numeric(14,3);
    v_custo_atual numeric(14,2);
    v_controla_estoque boolean;
    v_novo_estoque numeric(14,3);
    v_novo_custo numeric(14,2);
    v_total numeric(14,2) := 0;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    if p_itens is null or jsonb_array_length(p_itens) = 0 then
        raise exception 'A compra precisa possuir pelo menos um produto.';
    end if;

    insert into public.compras (
        empresa_id,
        fornecedor,
        data_compra,
        total,
        observacoes
    )
    values (
        p_empresa_id,
        p_fornecedor,
        p_data_compra,
        0,
        p_observacoes
    )
    returning compras.id into v_compra_id;

    for v_item in
        select item
          from jsonb_array_elements(p_itens) as itens(item)
    loop
        v_produto_id := (v_item ->> 'produto_id')::uuid;
        v_quantidade := (v_item ->> 'quantidade')::numeric;
        v_custo_unitario := (v_item ->> 'custo_unitario')::numeric;

        if v_quantidade <= 0 then
            raise exception 'Quantidade inválida.';
        end if;
        if v_custo_unitario < 0 then
            raise exception 'Custo inválido.';
        end if;

        select p.estoque, p.custo, p.controla_estoque
          into v_estoque_atual, v_custo_atual, v_controla_estoque
          from public.produtos p
         where p.id = v_produto_id
           and p.empresa_id = p_empresa_id
           and p.ativo = true
         for update;

        if not found then
            raise exception 'Produto não encontrado ou não pertence a esta empresa.';
        end if;

        if v_controla_estoque then
            v_novo_estoque := v_estoque_atual + v_quantidade;
            if v_novo_estoque > 0 then
                v_novo_custo := (
                    (v_custo_atual * v_estoque_atual) +
                    (v_custo_unitario * v_quantidade)
                ) / v_novo_estoque;
            else
                v_novo_custo := v_custo_unitario;
            end if;

            update public.produtos p
               set estoque = v_novo_estoque,
                   custo = v_novo_custo
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        else
            -- Sem controle de estoque: atualiza apenas o custo informado.
            update public.produtos p
               set custo = v_custo_unitario
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        end if;

        insert into public.compra_itens (
            empresa_id,
            compra_id,
            produto_id,
            quantidade,
            custo_unitario,
            total
        )
        values (
            p_empresa_id,
            v_compra_id,
            v_produto_id,
            v_quantidade,
            v_custo_unitario,
            v_quantidade * v_custo_unitario
        );

        v_total := v_total + (v_quantidade * v_custo_unitario);
    end loop;

    update public.compras c
       set total = v_total
     where c.id = v_compra_id
       and c.empresa_id = p_empresa_id;

    return v_compra_id;
end;
$$;


ALTER FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
  v_id uuid;
  v_old_storage_path text;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  perform 1
  from public.produtos p
  where p.id = p_produto_id
    and p.empresa_id = v_empresa_id
  for update;

  if not found then
    raise exception 'Produto não encontrado para esta empresa.';
  end if;

  if nullif(trim(coalesce(p_storage_path, '')), '') is null
     or nullif(trim(coalesce(p_url, '')), '') is null then
    raise exception 'Dados da foto inválidos.';
  end if;

  if split_part(p_storage_path, '/', 1) <> v_empresa_id::text
     or split_part(p_storage_path, '/', 2) <> p_produto_id::text then
    raise exception 'Caminho da foto inválido.';
  end if;

  select pf.id, pf.storage_path
    into v_id, v_old_storage_path
  from public.produto_fotos pf
  where pf.empresa_id = v_empresa_id
    and pf.produto_id = p_produto_id
  limit 1
  for update;

  if v_id is null then
    insert into public.produto_fotos (
      empresa_id, produto_id, storage_path, url, ordem
    ) values (
      v_empresa_id, p_produto_id, p_storage_path, p_url, 1
    )
    returning id into v_id;
  else
    update public.produto_fotos
    set storage_path = p_storage_path,
        url = p_url,
        ordem = 1,
        created_at = now()
    where id = v_id;
  end if;

  return jsonb_build_object(
    'id', v_id,
    'old_storage_path', v_old_storage_path
  );
end;
$$;


ALTER FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text" DEFAULT NULL::"text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_recebivel public.recebiveis_manuais%rowtype;
  v_recebido numeric(14,2);
  v_saldo numeric(14,2);
begin
  if not public.usuario_tem_acesso_empresa_v150(p_empresa_id) then
    raise exception 'Usuário sem acesso a esta empresa.';
  end if;

  select * into v_recebivel
  from public.recebiveis_manuais
  where id = p_recebivel_id
    and empresa_id = p_empresa_id
  for update;

  if v_recebivel.id is null then
    raise exception 'Lançamento manual não encontrado.';
  end if;

  if v_recebivel.status <> 'ABERTO' then
    raise exception 'Este lançamento já está quitado.';
  end if;

  if p_valor is null or p_valor <= 0 then
    raise exception 'O valor recebido deve ser maior que zero.';
  end if;

  select coalesce(sum(p.valor), 0)
    into v_recebido
  from public.recebivel_manual_pagamentos p
  where p.recebivel_id = p_recebivel_id;

  v_saldo := greatest(0, v_recebivel.valor_original - v_recebido);

  if round(p_valor, 2) > v_saldo then
    raise exception 'O valor recebido não pode ser maior que o saldo em aberto.';
  end if;

  insert into public.recebivel_manual_pagamentos (
    empresa_id,
    recebivel_id,
    valor,
    data_recebimento,
    observacoes
  )
  values (
    p_empresa_id,
    p_recebivel_id,
    round(p_valor, 2),
    coalesce(p_data_recebimento, current_date),
    nullif(trim(coalesce(p_observacoes, '')), '')
  );

  if round(p_valor, 2) >= v_saldo then
    update public.recebiveis_manuais
    set status = 'QUITADO',
        quitado_em = now()
    where id = p_recebivel_id;
  end if;
end;
$$;


ALTER FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") RETURNS "uuid"
    LANGUAGE "plpgsql"
    AS $_$
declare
    v_total_venda numeric(14,2);
    v_status text;
    v_recebido numeric(14,2);
    v_saldo numeric(14,2);
    v_recebimento_id uuid;
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    if p_valor is null or p_valor <= 0 then
        raise exception 'Informe um valor de recebimento maior que zero.';
    end if;

    select v.total_venda, v.status_pagamento
      into v_total_venda, v_status
      from public.vendas v
     where v.id = p_venda_id
       and v.empresa_id = p_empresa_id
     for update;

    if not found then
        raise exception 'Venda não encontrada.';
    end if;

    if v_status = 'PAGO' then
        raise exception 'Esta venda já está quitada.';
    end if;

    select coalesce(sum(r.valor), 0)
      into v_recebido
      from public.venda_recebimentos r
     where r.empresa_id = p_empresa_id
       and r.venda_id = p_venda_id;

    v_saldo := v_total_venda - v_recebido;

    if v_saldo <= 0 then
        raise exception 'Esta venda não possui saldo a receber.';
    end if;

    if p_valor > v_saldo then
        raise exception 'O abatimento não pode ser maior que o saldo de R$ %.', to_char(v_saldo, 'FM999999990D00');
    end if;

    insert into public.venda_recebimentos (
        empresa_id,
        venda_id,
        valor,
        data_recebimento,
        observacoes,
        created_by
    ) values (
        p_empresa_id,
        p_venda_id,
        round(p_valor, 2),
        coalesce(p_data_recebimento, current_date),
        nullif(trim(coalesce(p_observacoes, '')), ''),
        auth.uid()
    )
    returning id into v_recebimento_id;

    if round(v_recebido + p_valor, 2) >= round(v_total_venda, 2) then
        update public.vendas v
           set status_pagamento = 'PAGO',
               pago_em = now()
         where v.id = p_venda_id
           and v.empresa_id = p_empresa_id;
    end if;

    return v_recebimento_id;
end;
$_$;


ALTER FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") RETURNS TABLE("id" "uuid", "numero" bigint)
    LANGUAGE "sql"
    AS $$
    select *
      from public.registrar_venda(
          p_empresa_id,
          p_cliente_id,
          p_data_venda,
          p_observacoes,
          p_itens,
          'PAGO'
      );
$$;


ALTER FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") RETURNS TABLE("id" "uuid", "numero" bigint)
    LANGUAGE "plpgsql"
    AS $$
declare
    v_venda_id uuid;
    v_numero_empresa bigint;
    v_cliente_nome text := 'Venda rápida';
    v_item jsonb;
    v_produto_id uuid;
    v_produto_nome text;
    v_quantidade numeric(14,3);
    v_estoque numeric(14,3);
    v_controla_estoque boolean;
    v_custo_unitario numeric(14,2);
    v_preco_unitario numeric(14,2);
    v_total_custo_item numeric(14,2);
    v_total_venda_item numeric(14,2);
    v_lucro_item numeric(14,2);
    v_total_custo numeric(14,2) := 0;
    v_total_venda numeric(14,2) := 0;
    v_lucro numeric(14,2) := 0;
    v_status_pagamento text := upper(coalesce(p_status_pagamento, 'PAGO'));
begin
    if not public.usuario_tem_acesso_empresa(p_empresa_id) then
        raise exception 'Usuário sem acesso a esta empresa.';
    end if;

    if v_status_pagamento not in ('PAGO', 'A_RECEBER') then
        raise exception 'Status de pagamento inválido.';
    end if;

    if p_cliente_id is null and v_status_pagamento = 'A_RECEBER' then
        raise exception 'Venda fiada / a receber exige um cliente cadastrado.';
    end if;

    if p_itens is null or jsonb_array_length(p_itens) = 0 then
        raise exception 'A venda precisa possuir pelo menos um produto.';
    end if;

    if p_cliente_id is not null then
        select c.nome
          into v_cliente_nome
          from public.clientes c
         where c.id = p_cliente_id
           and c.empresa_id = p_empresa_id
           and c.ativo = true;

        if not found then
            raise exception 'Cliente não encontrado, inativo ou pertencente a outra empresa.';
        end if;
    end if;

    perform pg_advisory_xact_lock(hashtext(p_empresa_id::text)::bigint);

    select coalesce(max(v.numero_empresa), 0) + 1
      into v_numero_empresa
      from public.vendas v
     where v.empresa_id = p_empresa_id;

    insert into public.vendas (
        empresa_id,
        numero_empresa,
        cliente_id,
        cliente_nome,
        data_venda,
        total_custo,
        total_venda,
        lucro,
        observacoes,
        status_pagamento,
        pago_em
    )
    values (
        p_empresa_id,
        v_numero_empresa,
        p_cliente_id,
        v_cliente_nome,
        p_data_venda::timestamp at time zone 'America/Sao_Paulo',
        0,
        0,
        0,
        p_observacoes,
        v_status_pagamento,
        case when v_status_pagamento = 'PAGO' then now() else null end
    )
    returning vendas.id into v_venda_id;

    for v_item in
        select item
          from jsonb_array_elements(p_itens) as itens(item)
    loop
        v_produto_id := (v_item ->> 'produto_id')::uuid;
        v_quantidade := (v_item ->> 'quantidade')::numeric;
        v_preco_unitario := (v_item ->> 'preco_unitario')::numeric;

        if v_quantidade <= 0 then
            raise exception 'Quantidade inválida.';
        end if;

        if v_preco_unitario < 0 then
            raise exception 'Preço de venda inválido.';
        end if;

        select p.nome, p.estoque, p.custo, p.controla_estoque
          into v_produto_nome, v_estoque, v_custo_unitario, v_controla_estoque
          from public.produtos p
         where p.id = v_produto_id
           and p.empresa_id = p_empresa_id
           and p.ativo = true
         for update;

        if not found then
            raise exception 'Produto não encontrado, inativo ou pertencente a outra empresa.';
        end if;

        if v_controla_estoque and v_estoque < v_quantidade then
            raise exception
                'Estoque insuficiente para %. Disponível: %',
                v_produto_nome,
                v_estoque;
        end if;

        v_total_custo_item := v_quantidade * v_custo_unitario;
        v_total_venda_item := v_quantidade * v_preco_unitario;
        v_lucro_item := v_total_venda_item - v_total_custo_item;

        insert into public.venda_itens (
            empresa_id,
            venda_id,
            produto_id,
            produto_nome,
            quantidade,
            custo_unitario,
            preco_unitario,
            total_custo,
            total_venda,
            lucro
        )
        values (
            p_empresa_id,
            v_venda_id,
            v_produto_id,
            v_produto_nome,
            v_quantidade,
            v_custo_unitario,
            v_preco_unitario,
            v_total_custo_item,
            v_total_venda_item,
            v_lucro_item
        );

        if v_controla_estoque then
            update public.produtos p
               set estoque = p.estoque - v_quantidade
             where p.id = v_produto_id
               and p.empresa_id = p_empresa_id;
        end if;

        v_total_custo := v_total_custo + v_total_custo_item;
        v_total_venda := v_total_venda + v_total_venda_item;
        v_lucro := v_lucro + v_lucro_item;
    end loop;

    update public.vendas v
       set total_custo = v_total_custo,
           total_venda = v_total_venda,
           lucro = v_lucro
     where v.id = v_venda_id
       and v.empresa_id = p_empresa_id;

    return query
    select v_venda_id, v_numero_empresa;
end;
$$;


ALTER FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
  v_storage_path text;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  delete from public.produto_fotos pf
  where pf.id = p_foto_id
    and pf.produto_id = p_produto_id
    and pf.empresa_id = v_empresa_id
  returning pf.storage_path into v_storage_path;

  if v_storage_path is null then
    raise exception 'Foto não encontrada para este produto.';
  end if;

  return v_storage_path;
end;
$$;


ALTER FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
  v_perfil text;
  v_nome text := btrim(coalesce(p_nome, ''));
begin
  select to_jsonb(ctx)
    into v_contexto
    from public.obter_contexto_usuario() ctx
   limit 1;

  v_empresa_id := nullif(v_contexto ->> 'empresa_id', '')::uuid;
  v_perfil := upper(coalesce(v_contexto ->> 'perfil', ''));

  if v_empresa_id is null or v_perfil <> 'OWNER' then
    raise exception 'Somente o proprietário (OWNER) pode editar grupos de produtos.' using errcode = '42501';
  end if;

  if v_nome = '' then
    raise exception 'Informe o nome do grupo.' using errcode = '22023';
  end if;

  if exists (
    select 1 from public.produto_grupos g
     where g.empresa_id = v_empresa_id
       and g.id <> p_grupo_id
       and lower(btrim(g.nome)) = lower(v_nome)
  ) then
    raise exception 'Já existe um grupo com este nome.' using errcode = '23505';
  end if;

  update public.produto_grupos g
     set nome = v_nome,
         updated_at = now()
   where g.id = p_grupo_id
     and g.empresa_id = v_empresa_id;

  if not found then
    raise exception 'Grupo de produtos não encontrado.' using errcode = 'P0002';
  end if;
end;
$$;


ALTER FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
declare
  v_contexto jsonb;
  v_empresa_id uuid;
  v_perfil text;
  v_total integer;
  v_recebidos integer;
begin
  select to_jsonb(ctx)
    into v_contexto
    from public.obter_contexto_usuario() ctx
   limit 1;

  v_empresa_id := nullif(v_contexto ->> 'empresa_id', '')::uuid;
  v_perfil := upper(coalesce(v_contexto ->> 'perfil', ''));

  if v_empresa_id is null or v_perfil <> 'OWNER' then
    raise exception 'Somente o proprietário (OWNER) pode reordenar grupos de produtos.' using errcode = '42501';
  end if;

  select count(*) into v_total
    from public.produto_grupos g
   where g.empresa_id = v_empresa_id;

  select count(distinct item.id) into v_recebidos
    from unnest(coalesce(p_grupo_ids, array[]::uuid[])) as item(id)
    join public.produto_grupos g
      on g.id = item.id
     and g.empresa_id = v_empresa_id;

  if coalesce(array_length(p_grupo_ids, 1), 0) <> v_total or v_recebidos <> v_total then
    raise exception 'A nova ordem precisa conter todos os grupos da empresa uma única vez.' using errcode = '22023';
  end if;

  update public.produto_grupos g
     set ordem = ordered.posicao,
         updated_at = now()
    from (
      select item.id, item.ordinality::integer as posicao
        from unnest(p_grupo_ids) with ordinality as item(id, ordinality)
    ) ordered
   where g.id = ordered.id
     and g.empresa_id = v_empresa_id;
end;
$$;


ALTER FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  v_empresa_id uuid;
begin
  v_empresa_id := public.empresa_usuario_atual_pedidos();

  if not exists (
    select 1
    from public.clientes c
    where c.id = p_cliente_id
      and c.empresa_id = v_empresa_id
  ) then
    raise exception 'Cliente não encontrado para esta empresa.';
  end if;

  delete from public.cliente_produtos_ocultos cpo
  where cpo.empresa_id = v_empresa_id
    and cpo.cliente_id = p_cliente_id;

  insert into public.cliente_produtos_ocultos (empresa_id, cliente_id, produto_id)
  select v_empresa_id, p_cliente_id, p.id
  from public.produtos p
  where p.empresa_id = v_empresa_id
    and p.id = any(coalesce(p_produto_ids, array[]::uuid[]))
  group by p.id;
end;
$$;


ALTER FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    SET "search_path" TO ''
    AS $$
begin
  new.updated_at = now();
  return new;
end;
$$;


ALTER FUNCTION "public"."set_updated_at"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    select exists (
        select 1
          from public.empresa_usuarios eu
          join public.empresas e on e.id = eu.empresa_id
         where eu.empresa_id = p_empresa_id
           and eu.user_id = auth.uid()
           and eu.ativo = true
           and e.ativa = true
           and eu.perfil in ('OWNER', 'ADMIN')
    );
$$;


ALTER FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."usuario_e_admin_plataforma"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    select exists (
        select 1
          from public.plataforma_administradores pa
         where pa.user_id = auth.uid()
           and pa.ativo = true
    );
$$;


ALTER FUNCTION "public"."usuario_e_admin_plataforma"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    select exists (
        select 1
          from public.empresa_usuarios eu
          join public.empresas e on e.id = eu.empresa_id
         where eu.empresa_id = p_empresa_id
           and eu.user_id = auth.uid()
           and eu.ativo = true
           and e.ativa = true
    );
$$;


ALTER FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select exists (
    select 1
    from public.empresa_usuarios eu
    join public.empresas e on e.id = eu.empresa_id
    where eu.empresa_id = p_empresa_id
      and eu.user_id = auth.uid()
      and eu.ativo = true
      and e.ativa = true
  );
$$;


ALTER FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validar_compra_item_mesma_empresa"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    if not exists (
        select 1 from public.compras c
         where c.id = new.compra_id and c.empresa_id = new.empresa_id
    ) then
        raise exception 'Compra não pertence à empresa informada.';
    end if;

    if not exists (
        select 1 from public.produtos p
         where p.id = new.produto_id and p.empresa_id = new.empresa_id
    ) then
        raise exception 'Produto não pertence à empresa informada.';
    end if;

    return new;
end;
$$;


ALTER FUNCTION "public"."validar_compra_item_mesma_empresa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validar_preco_mesma_empresa"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    if not exists (
        select 1 from public.clientes c
         where c.id = new.cliente_id and c.empresa_id = new.empresa_id
    ) then
        raise exception 'Cliente não pertence à empresa informada.';
    end if;

    if not exists (
        select 1 from public.produtos p
         where p.id = new.produto_id and p.empresa_id = new.empresa_id
    ) then
        raise exception 'Produto não pertence à empresa informada.';
    end if;

    return new;
end;
$$;


ALTER FUNCTION "public"."validar_preco_mesma_empresa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validar_venda_item_mesma_empresa"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    if not exists (
        select 1 from public.vendas v
         where v.id = new.venda_id and v.empresa_id = new.empresa_id
    ) then
        raise exception 'Venda não pertence à empresa informada.';
    end if;

    if not exists (
        select 1 from public.produtos p
         where p.id = new.produto_id and p.empresa_id = new.empresa_id
    ) then
        raise exception 'Produto não pertence à empresa informada.';
    end if;

    return new;
end;
$$;


ALTER FUNCTION "public"."validar_venda_item_mesma_empresa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validar_venda_mesma_empresa"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    if new.cliente_id is not null and not exists (
        select 1 from public.clientes c
         where c.id = new.cliente_id and c.empresa_id = new.empresa_id
    ) then
        raise exception 'Cliente não pertence à empresa informada.';
    end if;

    return new;
end;
$$;


ALTER FUNCTION "public"."validar_venda_mesma_empresa"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."verificar_acesso_sistema"() RETURNS "text"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    select coalesce((select c.status from public.obter_contexto_usuario() c limit 1), 'NAO_AUTENTICADO');
$$;


ALTER FUNCTION "public"."verificar_acesso_sistema"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."vincular_usuario_empresa"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text" DEFAULT 'SELLER'::"text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $$
declare
    v_user_id uuid;
    v_vinculo_id uuid;
begin
    if p_perfil not in ('OWNER', 'ADMIN', 'MANAGER', 'SELLER') then
        raise exception 'Perfil inválido.';
    end if;

    if not exists (select 1 from public.empresas e where e.id = p_empresa_id) then
        raise exception 'Empresa não encontrada.';
    end if;

    select u.id
      into v_user_id
      from auth.users u
     where lower(u.email) = lower(trim(p_email))
     limit 1;

    if v_user_id is null then
        raise exception 'Usuário % não encontrado em Authentication > Users.', p_email;
    end if;

    insert into public.empresa_usuarios (empresa_id, user_id, perfil, ativo)
    values (p_empresa_id, v_user_id, p_perfil, true)
    on conflict (empresa_id, user_id)
    do update set perfil = excluded.perfil, ativo = true
    returning id into v_vinculo_id;

    return v_vinculo_id;
end;
$$;


ALTER FUNCTION "public"."vincular_usuario_empresa"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text" DEFAULT 'SELLER'::"text") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth', 'pg_temp'
    AS $$
declare
    v_user_id uuid;
    v_vinculo_id uuid;
begin
    if not public.usuario_e_admin_plataforma() then
        raise exception 'Acesso restrito ao administrador da plataforma.';
    end if;

    if p_perfil not in ('OWNER', 'ADMIN', 'MANAGER', 'SELLER') then
        raise exception 'Perfil inválido.';
    end if;

    if not exists (select 1 from public.empresas e where e.id = p_empresa_id) then
        raise exception 'Empresa não encontrada.';
    end if;

    select u.id
      into v_user_id
      from auth.users u
     where lower(u.email) = lower(trim(p_email))
     limit 1;

    if v_user_id is null then
        raise exception 'Usuário % não encontrado em Authentication > Users. Crie o usuário primeiro no Supabase Auth.', p_email;
    end if;

    if exists (
        select 1
          from public.empresa_usuarios eu
         where eu.user_id = v_user_id
           and eu.empresa_id <> p_empresa_id
           and eu.ativo = true
    ) then
        raise exception 'Este usuário já está vinculado a outra empresa ativa.';
    end if;

    insert into public.empresa_usuarios (
        empresa_id,
        user_id,
        perfil,
        ativo
    ) values (
        p_empresa_id,
        v_user_id,
        p_perfil,
        true
    )
    on conflict (empresa_id, user_id)
    do update set
        perfil = excluded.perfil,
        ativo = true
    returning id into v_vinculo_id;

    return v_vinculo_id;
end;
$$;


ALTER FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."cliente_produto_preco" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "cliente_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "tipo" character varying(20) DEFAULT 'taxa'::character varying NOT NULL,
    "taxa" numeric(10,4),
    "preco_fixo" numeric(14,2),
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    CONSTRAINT "tipo_preco_valido" CHECK ((("tipo")::"text" = ANY ((ARRAY['taxa'::character varying, 'preco_fixo'::character varying])::"text"[])))
);


ALTER TABLE "public"."cliente_produto_preco" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."cliente_produtos_ocultos" (
    "empresa_id" "uuid" NOT NULL,
    "cliente_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."cliente_produtos_ocultos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."clientes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "nome" character varying(150) NOT NULL,
    "razao_social" character varying(150),
    "cpf_cnpj" character varying(30),
    "telefone" character varying(30),
    "whatsapp" character varying(30),
    "endereco" "text",
    "cidade" character varying(100),
    "taxa_padrao" numeric(10,4) DEFAULT 0,
    "observacoes" "text",
    "ativo" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "pedido_token" "uuid" DEFAULT "gen_random_uuid"() NOT NULL
);


ALTER TABLE "public"."clientes" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."compra_itens" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "compra_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "quantidade" numeric(14,3) NOT NULL,
    "custo_unitario" numeric(14,2) NOT NULL,
    "total" numeric(14,2) NOT NULL,
    "empresa_id" "uuid" NOT NULL
);


ALTER TABLE "public"."compra_itens" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."compras" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "fornecedor" character varying(150),
    "data_compra" "date" DEFAULT CURRENT_DATE NOT NULL,
    "total" numeric(14,2) DEFAULT 0 NOT NULL,
    "observacoes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "empresa_id" "uuid" NOT NULL
);


ALTER TABLE "public"."compras" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."empresa_usuarios" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "perfil" "text" DEFAULT 'SELLER'::"text" NOT NULL,
    "ativo" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "empresa_usuarios_perfil_check" CHECK (("perfil" = ANY (ARRAY['OWNER'::"text", 'ADMIN'::"text", 'MANAGER'::"text", 'SELLER'::"text"])))
);


ALTER TABLE "public"."empresa_usuarios" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."empresas" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "slug" "text" NOT NULL,
    "nome_fantasia" "text" NOT NULL,
    "razao_social" "text",
    "cpf_cnpj" "text",
    "telefone" "text",
    "email" "text",
    "endereco" "text",
    "cidade" "text",
    "logo_url" "text",
    "cor_primaria" "text" DEFAULT '#f59e0b'::"text" NOT NULL,
    "cor_secundaria" "text" DEFAULT '#101827'::"text" NOT NULL,
    "ativa" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "empresas_cor_primaria_check" CHECK (("cor_primaria" ~ '^#[0-9A-Fa-f]{6}$'::"text")),
    CONSTRAINT "empresas_cor_secundaria_check" CHECK (("cor_secundaria" ~ '^#[0-9A-Fa-f]{6}$'::"text"))
);


ALTER TABLE "public"."empresas" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."pedido_cliente_itens" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "pedido_id" "uuid" NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "produto_nome" "text" NOT NULL,
    "quantidade" numeric(14,3) NOT NULL,
    "preco_unitario" numeric(14,2) NOT NULL,
    "total" numeric(14,2) NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "pedido_cliente_itens_preco_unitario_check" CHECK (("preco_unitario" >= (0)::numeric)),
    CONSTRAINT "pedido_cliente_itens_quantidade_check" CHECK (("quantidade" > (0)::numeric)),
    CONSTRAINT "pedido_cliente_itens_total_check" CHECK (("total" >= (0)::numeric))
);


ALTER TABLE "public"."pedido_cliente_itens" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."pedidos_cliente" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "cliente_id" "uuid" NOT NULL,
    "status" "text" DEFAULT 'PENDENTE'::"text" NOT NULL,
    "visualizado_em" timestamp with time zone,
    "observacoes" "text",
    "total" numeric(14,2) DEFAULT 0 NOT NULL,
    "venda_id" "uuid",
    "conversao_por" "uuid",
    "conversao_em" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "pedidos_cliente_status_check" CHECK (("status" = ANY (ARRAY['PENDENTE'::"text", 'CONVERTIDO'::"text", 'RECUSADO'::"text"])))
);


ALTER TABLE "public"."pedidos_cliente" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."plataforma_administradores" (
    "user_id" "uuid" NOT NULL,
    "ativo" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."plataforma_administradores" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."produto_fotos" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "storage_path" "text" NOT NULL,
    "url" "text" NOT NULL,
    "ordem" integer NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "produto_fotos_ordem_check" CHECK (("ordem" >= 1))
);


ALTER TABLE "public"."produto_fotos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."produto_grupos" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "nome" "text" NOT NULL,
    "ordem" integer DEFAULT 1 NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "produto_grupos_nome_check" CHECK (("length"("btrim"("nome")) > 0)),
    CONSTRAINT "produto_grupos_ordem_check" CHECK (("ordem" > 0))
);


ALTER TABLE "public"."produto_grupos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."produtos" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "nome" character varying(150) NOT NULL,
    "marca" character varying(100),
    "codigo" character varying(50),
    "descricao" "text",
    "custo" numeric(14,2) DEFAULT 0 NOT NULL,
    "preco_padrao" numeric(14,2) DEFAULT 0 NOT NULL,
    "estoque" numeric(14,3) DEFAULT 0 NOT NULL,
    "estoque_minimo" numeric(14,3) DEFAULT 0 NOT NULL,
    "ativo" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "controla_estoque" boolean DEFAULT false NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "grupo_id" "uuid"
);


ALTER TABLE "public"."produtos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."profiles" (
    "id" "uuid" NOT NULL,
    "full_name" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "role" "text" DEFAULT 'owner'::"text" NOT NULL,
    "student_id" "uuid",
    "owner_id" "uuid",
    CONSTRAINT "profiles_role_check" CHECK (("role" = ANY (ARRAY['owner'::"text", 'student'::"text"])))
);


ALTER TABLE "public"."profiles" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."recebiveis_manuais" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "cliente_id" "uuid" NOT NULL,
    "valor_original" numeric(14,2) NOT NULL,
    "data_lancamento" "date" DEFAULT CURRENT_DATE NOT NULL,
    "observacoes" "text",
    "status" "text" DEFAULT 'ABERTO'::"text" NOT NULL,
    "quitado_em" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "recebiveis_manuais_status_check" CHECK (("status" = ANY (ARRAY['ABERTO'::"text", 'QUITADO'::"text"]))),
    CONSTRAINT "recebiveis_manuais_valor_original_check" CHECK (("valor_original" > (0)::numeric))
);


ALTER TABLE "public"."recebiveis_manuais" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."recebivel_manual_pagamentos" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "recebivel_id" "uuid" NOT NULL,
    "valor" numeric(14,2) NOT NULL,
    "data_recebimento" "date" DEFAULT CURRENT_DATE NOT NULL,
    "observacoes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "recebivel_manual_pagamentos_valor_check" CHECK (("valor" > (0)::numeric))
);


ALTER TABLE "public"."recebivel_manual_pagamentos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."venda_itens" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "venda_id" "uuid" NOT NULL,
    "produto_id" "uuid" NOT NULL,
    "quantidade" numeric(14,3) NOT NULL,
    "custo_unitario" numeric(14,2) NOT NULL,
    "preco_unitario" numeric(14,2) NOT NULL,
    "total_custo" numeric(14,2) NOT NULL,
    "total_venda" numeric(14,2) NOT NULL,
    "lucro" numeric(14,2) NOT NULL,
    "produto_nome" "text",
    "empresa_id" "uuid" NOT NULL
);


ALTER TABLE "public"."venda_itens" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."venda_recebimentos" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "empresa_id" "uuid" NOT NULL,
    "venda_id" "uuid" NOT NULL,
    "valor" numeric(14,2) NOT NULL,
    "data_recebimento" "date" DEFAULT CURRENT_DATE NOT NULL,
    "observacoes" "text",
    "created_by" "uuid" DEFAULT "auth"."uid"(),
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "venda_recebimentos_valor_check" CHECK (("valor" > (0)::numeric))
);


ALTER TABLE "public"."venda_recebimentos" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."vendas" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "numero" bigint NOT NULL,
    "cliente_id" "uuid",
    "data_venda" timestamp with time zone DEFAULT "now"() NOT NULL,
    "total_custo" numeric(14,2) DEFAULT 0 NOT NULL,
    "total_venda" numeric(14,2) DEFAULT 0 NOT NULL,
    "lucro" numeric(14,2) DEFAULT 0 NOT NULL,
    "observacoes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "cliente_nome" "text",
    "empresa_id" "uuid" NOT NULL,
    "numero_empresa" bigint NOT NULL,
    "status_pagamento" "text" DEFAULT 'PAGO'::"text" NOT NULL,
    "pago_em" timestamp with time zone,
    CONSTRAINT "vendas_status_pagamento_check" CHECK (("status_pagamento" = ANY (ARRAY['PAGO'::"text", 'A_RECEBER'::"text"])))
);


ALTER TABLE "public"."vendas" OWNER TO "postgres";


ALTER TABLE "public"."vendas" ALTER COLUMN "numero" ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME "public"."vendas_numero_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



ALTER TABLE ONLY "public"."cliente_produto_preco"
    ADD CONSTRAINT "cliente_produto_preco_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."cliente_produto_preco"
    ADD CONSTRAINT "cliente_produto_unico" UNIQUE ("cliente_id", "produto_id");



ALTER TABLE ONLY "public"."cliente_produtos_ocultos"
    ADD CONSTRAINT "cliente_produtos_ocultos_pkey" PRIMARY KEY ("cliente_id", "produto_id");



ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "clientes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."compra_itens"
    ADD CONSTRAINT "compra_itens_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."compras"
    ADD CONSTRAINT "compras_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."empresa_usuarios"
    ADD CONSTRAINT "empresa_usuarios_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."empresa_usuarios"
    ADD CONSTRAINT "empresa_usuarios_unique" UNIQUE ("empresa_id", "user_id");



ALTER TABLE ONLY "public"."empresas"
    ADD CONSTRAINT "empresas_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."empresas"
    ADD CONSTRAINT "empresas_slug_key" UNIQUE ("slug");



ALTER TABLE ONLY "public"."pedido_cliente_itens"
    ADD CONSTRAINT "pedido_cliente_itens_pedido_id_produto_id_key" UNIQUE ("pedido_id", "produto_id");



ALTER TABLE ONLY "public"."pedido_cliente_itens"
    ADD CONSTRAINT "pedido_cliente_itens_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."pedidos_cliente"
    ADD CONSTRAINT "pedidos_cliente_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."plataforma_administradores"
    ADD CONSTRAINT "plataforma_administradores_pkey" PRIMARY KEY ("user_id");



ALTER TABLE ONLY "public"."produto_fotos"
    ADD CONSTRAINT "produto_fotos_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."produto_fotos"
    ADD CONSTRAINT "produto_fotos_storage_path_key" UNIQUE ("storage_path");



ALTER TABLE ONLY "public"."produto_fotos"
    ADD CONSTRAINT "produto_fotos_um_por_produto" UNIQUE ("produto_id");



ALTER TABLE ONLY "public"."produto_grupos"
    ADD CONSTRAINT "produto_grupos_id_empresa_id_key" UNIQUE ("id", "empresa_id");



ALTER TABLE ONLY "public"."produto_grupos"
    ADD CONSTRAINT "produto_grupos_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."produtos"
    ADD CONSTRAINT "produtos_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."recebiveis_manuais"
    ADD CONSTRAINT "recebiveis_manuais_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."recebivel_manual_pagamentos"
    ADD CONSTRAINT "recebivel_manual_pagamentos_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."venda_itens"
    ADD CONSTRAINT "venda_itens_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."venda_recebimentos"
    ADD CONSTRAINT "venda_recebimentos_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."vendas"
    ADD CONSTRAINT "vendas_pkey" PRIMARY KEY ("id");



CREATE INDEX "idx_cliente_produtos_ocultos_empresa_cliente" ON "public"."cliente_produtos_ocultos" USING "btree" ("empresa_id", "cliente_id");



CREATE INDEX "idx_clientes_empresa" ON "public"."clientes" USING "btree" ("empresa_id");



CREATE INDEX "idx_compra_itens_empresa" ON "public"."compra_itens" USING "btree" ("empresa_id");



CREATE INDEX "idx_compras_empresa" ON "public"."compras" USING "btree" ("empresa_id");



CREATE INDEX "idx_empresa_usuarios_empresa" ON "public"."empresa_usuarios" USING "btree" ("empresa_id");



CREATE INDEX "idx_empresa_usuarios_user" ON "public"."empresa_usuarios" USING "btree" ("user_id");



CREATE INDEX "idx_pedido_cliente_itens_pedido" ON "public"."pedido_cliente_itens" USING "btree" ("pedido_id");



CREATE INDEX "idx_pedidos_cliente_cliente" ON "public"."pedidos_cliente" USING "btree" ("cliente_id");



CREATE INDEX "idx_pedidos_cliente_empresa_created" ON "public"."pedidos_cliente" USING "btree" ("empresa_id", "created_at" DESC);



CREATE INDEX "idx_pedidos_cliente_status" ON "public"."pedidos_cliente" USING "btree" ("empresa_id", "status", "visualizado_em");



CREATE INDEX "idx_precos_empresa" ON "public"."cliente_produto_preco" USING "btree" ("empresa_id");



CREATE INDEX "idx_produto_fotos_empresa_produto" ON "public"."produto_fotos" USING "btree" ("empresa_id", "produto_id", "ordem");



CREATE INDEX "idx_produtos_empresa" ON "public"."produtos" USING "btree" ("empresa_id");



CREATE INDEX "idx_recebiveis_manuais_cliente" ON "public"."recebiveis_manuais" USING "btree" ("cliente_id", "data_lancamento" DESC);



CREATE INDEX "idx_recebiveis_manuais_empresa_status" ON "public"."recebiveis_manuais" USING "btree" ("empresa_id", "status", "data_lancamento" DESC);



CREATE INDEX "idx_recebivel_manual_pagamentos_recebivel" ON "public"."recebivel_manual_pagamentos" USING "btree" ("recebivel_id", "data_recebimento", "created_at");



CREATE INDEX "idx_venda_itens_empresa" ON "public"."venda_itens" USING "btree" ("empresa_id");



CREATE INDEX "idx_venda_recebimentos_empresa_venda" ON "public"."venda_recebimentos" USING "btree" ("empresa_id", "venda_id", "data_recebimento", "created_at");



CREATE INDEX "idx_vendas_empresa" ON "public"."vendas" USING "btree" ("empresa_id");



CREATE INDEX "idx_vendas_empresa_status_pagamento" ON "public"."vendas" USING "btree" ("empresa_id", "status_pagamento");



CREATE UNIQUE INDEX "produto_grupos_empresa_nome_ci_uidx" ON "public"."produto_grupos" USING "btree" ("empresa_id", "lower"("btrim"("nome")));



CREATE INDEX "produto_grupos_empresa_ordem_idx" ON "public"."produto_grupos" USING "btree" ("empresa_id", "ordem", "nome");



CREATE INDEX "produtos_grupo_id_idx" ON "public"."produtos" USING "btree" ("grupo_id");



CREATE INDEX "profiles_owner_idx" ON "public"."profiles" USING "btree" ("owner_id") WHERE ("owner_id" IS NOT NULL);



CREATE UNIQUE INDEX "profiles_student_unique_idx" ON "public"."profiles" USING "btree" ("student_id") WHERE ("student_id" IS NOT NULL);



CREATE UNIQUE INDEX "uq_clientes_pedido_token" ON "public"."clientes" USING "btree" ("pedido_token");



CREATE UNIQUE INDEX "uq_vendas_empresa_numero" ON "public"."vendas" USING "btree" ("empresa_id", "numero_empresa");



CREATE OR REPLACE TRIGGER "profiles_updated_at" BEFORE UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_compra_item_mesma_empresa" BEFORE INSERT OR UPDATE ON "public"."compra_itens" FOR EACH ROW EXECUTE FUNCTION "public"."validar_compra_item_mesma_empresa"();



CREATE OR REPLACE TRIGGER "trg_preco_mesma_empresa" BEFORE INSERT OR UPDATE ON "public"."cliente_produto_preco" FOR EACH ROW EXECUTE FUNCTION "public"."validar_preco_mesma_empresa"();



CREATE OR REPLACE TRIGGER "trg_venda_item_mesma_empresa" BEFORE INSERT OR UPDATE ON "public"."venda_itens" FOR EACH ROW EXECUTE FUNCTION "public"."validar_venda_item_mesma_empresa"();



CREATE OR REPLACE TRIGGER "trg_venda_mesma_empresa" BEFORE INSERT OR UPDATE ON "public"."vendas" FOR EACH ROW EXECUTE FUNCTION "public"."validar_venda_mesma_empresa"();



ALTER TABLE ONLY "public"."cliente_produto_preco"
    ADD CONSTRAINT "cliente_produto_preco_cliente_id_fkey" FOREIGN KEY ("cliente_id") REFERENCES "public"."clientes"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cliente_produto_preco"
    ADD CONSTRAINT "cliente_produto_preco_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."cliente_produto_preco"
    ADD CONSTRAINT "cliente_produto_preco_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cliente_produtos_ocultos"
    ADD CONSTRAINT "cliente_produtos_ocultos_cliente_id_fkey" FOREIGN KEY ("cliente_id") REFERENCES "public"."clientes"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cliente_produtos_ocultos"
    ADD CONSTRAINT "cliente_produtos_ocultos_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cliente_produtos_ocultos"
    ADD CONSTRAINT "cliente_produtos_ocultos_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."clientes"
    ADD CONSTRAINT "clientes_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."compra_itens"
    ADD CONSTRAINT "compra_itens_compra_id_fkey" FOREIGN KEY ("compra_id") REFERENCES "public"."compras"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."compra_itens"
    ADD CONSTRAINT "compra_itens_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."compra_itens"
    ADD CONSTRAINT "compra_itens_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id");



ALTER TABLE ONLY "public"."compras"
    ADD CONSTRAINT "compras_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."empresa_usuarios"
    ADD CONSTRAINT "empresa_usuarios_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."empresa_usuarios"
    ADD CONSTRAINT "empresa_usuarios_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."pedido_cliente_itens"
    ADD CONSTRAINT "pedido_cliente_itens_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."pedido_cliente_itens"
    ADD CONSTRAINT "pedido_cliente_itens_pedido_id_fkey" FOREIGN KEY ("pedido_id") REFERENCES "public"."pedidos_cliente"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."pedido_cliente_itens"
    ADD CONSTRAINT "pedido_cliente_itens_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."pedidos_cliente"
    ADD CONSTRAINT "pedidos_cliente_cliente_id_fkey" FOREIGN KEY ("cliente_id") REFERENCES "public"."clientes"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."pedidos_cliente"
    ADD CONSTRAINT "pedidos_cliente_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."plataforma_administradores"
    ADD CONSTRAINT "plataforma_administradores_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."produto_fotos"
    ADD CONSTRAINT "produto_fotos_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."produto_fotos"
    ADD CONSTRAINT "produto_fotos_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."produto_grupos"
    ADD CONSTRAINT "produto_grupos_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."produtos"
    ADD CONSTRAINT "produtos_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."produtos"
    ADD CONSTRAINT "produtos_grupo_empresa_fk" FOREIGN KEY ("grupo_id", "empresa_id") REFERENCES "public"."produto_grupos"("id", "empresa_id") ON DELETE SET NULL ("grupo_id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_owner_id_fkey" FOREIGN KEY ("owner_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."recebiveis_manuais"
    ADD CONSTRAINT "recebiveis_manuais_cliente_id_fkey" FOREIGN KEY ("cliente_id") REFERENCES "public"."clientes"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."recebiveis_manuais"
    ADD CONSTRAINT "recebiveis_manuais_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."recebivel_manual_pagamentos"
    ADD CONSTRAINT "recebivel_manual_pagamentos_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."recebivel_manual_pagamentos"
    ADD CONSTRAINT "recebivel_manual_pagamentos_recebivel_id_fkey" FOREIGN KEY ("recebivel_id") REFERENCES "public"."recebiveis_manuais"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."venda_itens"
    ADD CONSTRAINT "venda_itens_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."venda_itens"
    ADD CONSTRAINT "venda_itens_produto_id_fkey" FOREIGN KEY ("produto_id") REFERENCES "public"."produtos"("id");



ALTER TABLE ONLY "public"."venda_itens"
    ADD CONSTRAINT "venda_itens_venda_id_fkey" FOREIGN KEY ("venda_id") REFERENCES "public"."vendas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."venda_recebimentos"
    ADD CONSTRAINT "venda_recebimentos_empresa_id_fkey" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."venda_recebimentos"
    ADD CONSTRAINT "venda_recebimentos_venda_id_fkey" FOREIGN KEY ("venda_id") REFERENCES "public"."vendas"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."vendas"
    ADD CONSTRAINT "vendas_cliente_id_fkey" FOREIGN KEY ("cliente_id") REFERENCES "public"."clientes"("id");



ALTER TABLE ONLY "public"."vendas"
    ADD CONSTRAINT "vendas_empresa_fk" FOREIGN KEY ("empresa_id") REFERENCES "public"."empresas"("id") ON DELETE RESTRICT;



ALTER TABLE "public"."cliente_produto_preco" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."cliente_produtos_ocultos" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "cliente_produtos_ocultos_sem_acesso_direto" ON "public"."cliente_produtos_ocultos" USING (false) WITH CHECK (false);



ALTER TABLE "public"."clientes" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "clientes_empresa" ON "public"."clientes" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."compra_itens" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "compra_itens_empresa" ON "public"."compra_itens" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."compras" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "compras_empresa" ON "public"."compras" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."empresa_usuarios" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "empresa_usuarios_select" ON "public"."empresa_usuarios" FOR SELECT TO "authenticated" USING ((("user_id" = "auth"."uid"()) OR "public"."usuario_e_admin_empresa"("empresa_id")));



ALTER TABLE "public"."empresas" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "empresas_select_membro" ON "public"."empresas" FOR SELECT TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("id"));



CREATE POLICY "empresas_update_admin" ON "public"."empresas" FOR UPDATE TO "authenticated" USING ("public"."usuario_e_admin_empresa"("id")) WITH CHECK ("public"."usuario_e_admin_empresa"("id"));



ALTER TABLE "public"."pedido_cliente_itens" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "pedido_cliente_itens_sem_acesso_direto" ON "public"."pedido_cliente_itens" USING (false) WITH CHECK (false);



ALTER TABLE "public"."pedidos_cliente" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "pedidos_cliente_sem_acesso_direto" ON "public"."pedidos_cliente" USING (false) WITH CHECK (false);



ALTER TABLE "public"."plataforma_administradores" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "plataforma_administradores_select_self" ON "public"."plataforma_administradores" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "precos_empresa" ON "public"."cliente_produto_preco" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."produto_fotos" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "produto_fotos_select_empresa" ON "public"."produto_fotos" FOR SELECT TO "authenticated" USING (("empresa_id" = "public"."empresa_usuario_atual_pedidos"()));



CREATE POLICY "produto_fotos_sem_escrita_direta" ON "public"."produto_fotos" TO "authenticated" USING (false) WITH CHECK (false);



ALTER TABLE "public"."produto_grupos" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."produtos" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "produtos_empresa" ON "public"."produtos" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."profiles" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "profiles_owner_select_students" ON "public"."profiles" FOR SELECT USING ((("role" = 'student'::"text") AND ("owner_id" = "auth"."uid"())));



CREATE POLICY "profiles_select_own" ON "public"."profiles" FOR SELECT USING (("id" = "auth"."uid"()));



ALTER TABLE "public"."recebiveis_manuais" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "recebiveis_manuais_select_empresa" ON "public"."recebiveis_manuais" FOR SELECT TO "authenticated" USING ("public"."usuario_tem_acesso_empresa_v150"("empresa_id"));



ALTER TABLE "public"."recebivel_manual_pagamentos" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "recebivel_manual_pagamentos_select_empresa" ON "public"."recebivel_manual_pagamentos" FOR SELECT TO "authenticated" USING ("public"."usuario_tem_acesso_empresa_v150"("empresa_id"));



ALTER TABLE "public"."venda_itens" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "venda_itens_empresa" ON "public"."venda_itens" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."venda_recebimentos" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "venda_recebimentos_empresa" ON "public"."venda_recebimentos" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));



ALTER TABLE "public"."vendas" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "vendas_empresa" ON "public"."vendas" TO "authenticated" USING ("public"."usuario_tem_acesso_empresa"("empresa_id")) WITH CHECK ("public"."usuario_tem_acesso_empresa"("empresa_id"));





ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";






















































































































































REVOKE ALL ON FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."alterar_status_empresa_plataforma"("p_empresa_id" "uuid", "p_ativa" boolean) TO "service_role";



REVOKE ALL ON FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."atualizar_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."atualizar_usuario_empresa_plataforma"("p_vinculo_id" "uuid", "p_perfil" "text", "p_ativo" boolean) TO "service_role";



REVOKE ALL ON FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."atualizar_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."atualizar_venda_com_pagamento"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_status_pagamento" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."cancelar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."criar_grupo_produto"("p_nome" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."criar_grupo_produto"("p_nome" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."criar_grupo_produto"("p_nome" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."criar_grupo_produto"("p_nome" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."criar_pedido_publico"("p_token" "uuid", "p_observacoes" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."criar_recebivel_manual"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_valor" numeric, "p_data_lancamento" "date", "p_observacoes" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."diagnosticar_meu_acesso"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."diagnosticar_meu_acesso"() TO "anon";
GRANT ALL ON FUNCTION "public"."diagnosticar_meu_acesso"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."diagnosticar_meu_acesso"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."empresa_usuario_atual_pedidos"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."empresa_usuario_atual_pedidos"() TO "anon";
GRANT ALL ON FUNCTION "public"."empresa_usuario_atual_pedidos"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."empresa_usuario_atual_pedidos"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."excluir_compra"("p_empresa_id" "uuid", "p_compra_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."excluir_empresa_plataforma"("p_empresa_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."excluir_grupo_produto"("p_grupo_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."excluir_pedido_cliente_recusado"("p_pedido_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."excluir_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."iniciar_conversao_pedido_cliente"("p_pedido_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."listar_empresas_plataforma"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."listar_empresas_plataforma"() TO "anon";
GRANT ALL ON FUNCTION "public"."listar_empresas_plataforma"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."listar_empresas_plataforma"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."listar_grupos_produto"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."listar_grupos_produto"() TO "anon";
GRANT ALL ON FUNCTION "public"."listar_grupos_produto"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."listar_grupos_produto"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."listar_pedidos_cliente"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."listar_pedidos_cliente"() TO "anon";
GRANT ALL ON FUNCTION "public"."listar_pedidos_cliente"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."listar_pedidos_cliente"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."listar_produtos_ocultos_cliente"("p_cliente_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."listar_usuarios_empresa_plataforma"("p_empresa_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_convertido"("p_pedido_id" "uuid", "p_venda_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."marcar_pedido_cliente_visualizado"("p_pedido_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."marcar_venda_como_paga"("p_empresa_id" "uuid", "p_venda_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."obter_catalogo_pedido_publico"("p_token" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."obter_contexto_usuario"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."obter_contexto_usuario"() TO "anon";
GRANT ALL ON FUNCTION "public"."obter_contexto_usuario"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."obter_contexto_usuario"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."obter_grupos_pedido_publico"("p_token" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."obter_ultimo_pedido_cliente_publico"("p_token" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."provisionar_empresa"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."provisionar_empresa"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text", "p_cor_secundaria" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text", "p_cor_secundaria" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text", "p_cor_secundaria" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."provisionar_empresa_plataforma"("p_nome_fantasia" "text", "p_razao_social" "text", "p_cpf_cnpj" "text", "p_email_admin" "text", "p_cor_primaria" "text", "p_cor_secundaria" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."quitar_recebivel_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."recusar_pedido_cliente"("p_pedido_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."regenerar_link_pedido_cliente"("p_cliente_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_compra"("p_empresa_id" "uuid", "p_fornecedor" "text", "p_data_compra" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_foto_produto"("p_produto_id" "uuid", "p_storage_path" "text", "p_url" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_recebimento_manual"("p_empresa_id" "uuid", "p_recebivel_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_recebimento_venda"("p_empresa_id" "uuid", "p_venda_id" "uuid", "p_valor" numeric, "p_data_recebimento" "date", "p_observacoes" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb") TO "service_role";



REVOKE ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."registrar_venda"("p_empresa_id" "uuid", "p_cliente_id" "uuid", "p_data_venda" "date", "p_observacoes" "text", "p_itens" "jsonb", "p_status_pagamento" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."remover_foto_produto"("p_produto_id" "uuid", "p_foto_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."renomear_grupo_produto"("p_grupo_id" "uuid", "p_nome" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."reordenar_grupos_produto"("p_grupo_ids" "uuid"[]) TO "service_role";



REVOKE ALL ON FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."salvar_produtos_ocultos_cliente"("p_cliente_id" "uuid", "p_produto_ids" "uuid"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."usuario_e_admin_empresa"("p_empresa_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."usuario_e_admin_plataforma"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."usuario_e_admin_plataforma"() TO "anon";
GRANT ALL ON FUNCTION "public"."usuario_e_admin_plataforma"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."usuario_e_admin_plataforma"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa"("p_empresa_id" "uuid") TO "service_role";



REVOKE ALL ON FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."usuario_tem_acesso_empresa_v150"("p_empresa_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."validar_compra_item_mesma_empresa"() TO "anon";
GRANT ALL ON FUNCTION "public"."validar_compra_item_mesma_empresa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."validar_compra_item_mesma_empresa"() TO "service_role";



GRANT ALL ON FUNCTION "public"."validar_preco_mesma_empresa"() TO "anon";
GRANT ALL ON FUNCTION "public"."validar_preco_mesma_empresa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."validar_preco_mesma_empresa"() TO "service_role";



GRANT ALL ON FUNCTION "public"."validar_venda_item_mesma_empresa"() TO "anon";
GRANT ALL ON FUNCTION "public"."validar_venda_item_mesma_empresa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."validar_venda_item_mesma_empresa"() TO "service_role";



GRANT ALL ON FUNCTION "public"."validar_venda_mesma_empresa"() TO "anon";
GRANT ALL ON FUNCTION "public"."validar_venda_mesma_empresa"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."validar_venda_mesma_empresa"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."verificar_acesso_sistema"() FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."verificar_acesso_sistema"() TO "anon";
GRANT ALL ON FUNCTION "public"."verificar_acesso_sistema"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."verificar_acesso_sistema"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."vincular_usuario_empresa"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."vincular_usuario_empresa"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."vincular_usuario_empresa_plataforma"("p_empresa_id" "uuid", "p_email" "text", "p_perfil" "text") TO "service_role";


















GRANT ALL ON TABLE "public"."cliente_produto_preco" TO "anon";
GRANT ALL ON TABLE "public"."cliente_produto_preco" TO "authenticated";
GRANT ALL ON TABLE "public"."cliente_produto_preco" TO "service_role";



GRANT ALL ON TABLE "public"."cliente_produtos_ocultos" TO "anon";
GRANT ALL ON TABLE "public"."cliente_produtos_ocultos" TO "authenticated";
GRANT ALL ON TABLE "public"."cliente_produtos_ocultos" TO "service_role";



GRANT ALL ON TABLE "public"."clientes" TO "anon";
GRANT ALL ON TABLE "public"."clientes" TO "authenticated";
GRANT ALL ON TABLE "public"."clientes" TO "service_role";



GRANT ALL ON TABLE "public"."compra_itens" TO "anon";
GRANT ALL ON TABLE "public"."compra_itens" TO "authenticated";
GRANT ALL ON TABLE "public"."compra_itens" TO "service_role";



GRANT ALL ON TABLE "public"."compras" TO "anon";
GRANT ALL ON TABLE "public"."compras" TO "authenticated";
GRANT ALL ON TABLE "public"."compras" TO "service_role";



GRANT ALL ON TABLE "public"."empresa_usuarios" TO "authenticated";
GRANT ALL ON TABLE "public"."empresa_usuarios" TO "service_role";



GRANT ALL ON TABLE "public"."empresas" TO "authenticated";
GRANT ALL ON TABLE "public"."empresas" TO "service_role";



GRANT ALL ON TABLE "public"."pedido_cliente_itens" TO "anon";
GRANT ALL ON TABLE "public"."pedido_cliente_itens" TO "authenticated";
GRANT ALL ON TABLE "public"."pedido_cliente_itens" TO "service_role";



GRANT ALL ON TABLE "public"."pedidos_cliente" TO "anon";
GRANT ALL ON TABLE "public"."pedidos_cliente" TO "authenticated";
GRANT ALL ON TABLE "public"."pedidos_cliente" TO "service_role";



GRANT ALL ON TABLE "public"."plataforma_administradores" TO "authenticated";
GRANT ALL ON TABLE "public"."plataforma_administradores" TO "service_role";



GRANT ALL ON TABLE "public"."produto_fotos" TO "anon";
GRANT ALL ON TABLE "public"."produto_fotos" TO "authenticated";
GRANT ALL ON TABLE "public"."produto_fotos" TO "service_role";



GRANT ALL ON TABLE "public"."produto_grupos" TO "service_role";



GRANT ALL ON TABLE "public"."produtos" TO "anon";
GRANT ALL ON TABLE "public"."produtos" TO "authenticated";
GRANT ALL ON TABLE "public"."produtos" TO "service_role";



GRANT ALL ON TABLE "public"."profiles" TO "anon";
GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."profiles" TO "authenticated";
GRANT ALL ON TABLE "public"."profiles" TO "service_role";



GRANT ALL ON TABLE "public"."recebiveis_manuais" TO "anon";
GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."recebiveis_manuais" TO "authenticated";
GRANT ALL ON TABLE "public"."recebiveis_manuais" TO "service_role";



GRANT ALL ON TABLE "public"."recebivel_manual_pagamentos" TO "anon";
GRANT SELECT,REFERENCES,TRIGGER,TRUNCATE,MAINTAIN ON TABLE "public"."recebivel_manual_pagamentos" TO "authenticated";
GRANT ALL ON TABLE "public"."recebivel_manual_pagamentos" TO "service_role";



GRANT ALL ON TABLE "public"."venda_itens" TO "anon";
GRANT ALL ON TABLE "public"."venda_itens" TO "authenticated";
GRANT ALL ON TABLE "public"."venda_itens" TO "service_role";



GRANT ALL ON TABLE "public"."venda_recebimentos" TO "authenticated";
GRANT ALL ON TABLE "public"."venda_recebimentos" TO "service_role";



GRANT ALL ON TABLE "public"."vendas" TO "anon";
GRANT ALL ON TABLE "public"."vendas" TO "authenticated";
GRANT ALL ON TABLE "public"."vendas" TO "service_role";



GRANT ALL ON SEQUENCE "public"."vendas_numero_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."vendas_numero_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."vendas_numero_seq" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";































