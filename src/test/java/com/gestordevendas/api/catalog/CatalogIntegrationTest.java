package com.gestordevendas.api.catalog;

import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.storage.ObjectStorage;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CatalogIntegrationTest extends PostgresIntegrationTest {
    private static final AtomicInteger LOGIN_IP_SEQUENCE = new AtomicInteger(1);
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean ObjectStorage objectStorage;

    private String ownerToken;
    private String adminToken;
    private String sellerToken;
    private String otherOwnerToken;

    @BeforeEach
    void setUp() throws Exception {
        Company company = companyRepository.save(Company.create("catalog-a", "Catalog A", null, null, null, null));
        Company other = companyRepository.save(Company.create("catalog-b", "Catalog B", null, null, null, null));

        User owner = userRepository.save(User.create("owner.catalog@example.com", "Owner", passwordEncoder.encode("Senha123")));
        User admin = userRepository.save(User.create("admin.catalog@example.com", "Admin", passwordEncoder.encode("Senha123")));
        User seller = userRepository.save(User.create("seller.catalog@example.com", "Seller", passwordEncoder.encode("Senha123")));
        User otherOwner = userRepository.save(User.create("owner.other@example.com", "Other", passwordEncoder.encode("Senha123")));
        membershipRepository.save(CompanyMembership.create(company, owner, CompanyRole.OWNER));
        membershipRepository.save(CompanyMembership.create(company, admin, CompanyRole.ADMIN));
        membershipRepository.save(CompanyMembership.create(company, seller, CompanyRole.VENDEDOR));
        membershipRepository.save(CompanyMembership.create(other, otherOwner, CompanyRole.OWNER));

        ownerToken = login("owner.catalog@example.com");
        adminToken = login("admin.catalog@example.com");
        sellerToken = login("seller.catalog@example.com");
        otherOwnerToken = login("owner.other@example.com");

        when(objectStorage.put(anyString(), anyString(), any(byte[].class)))
            .thenAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0, String.class));
    }

    @Test
    void adminManagesClientsSellerOnlyReadsAndTenantsAreIsolated() throws Exception {
        String clientId = createClient(adminToken, "Cliente A");

        mvc.perform(get("/api/clients").header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cliente A"));

        mvc.perform(post("/api/clients")
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bloqueado\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/clients/{id}", clientId).header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));

        mvc.perform(delete("/api/clients/{id}", clientId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/clients/{id}", clientId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void productResponseOmitsCostAndMarginForSeller() throws Exception {
        String categoryId = createCategory(adminToken, "Bebidas");
        String productId = createProduct(adminToken, categoryId, "Produto A", "100.00", "120.00");

        mvc.perform(get("/api/products/{id}", productId).header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.salePrice").value(120.00))
            .andExpect(jsonPath("$.costPrice").value(100.00))
            .andExpect(jsonPath("$.marginPercent").value(16.67));

        mvc.perform(get("/api/products/{id}", productId).header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.salePrice").value(120.00))
            .andExpect(jsonPath("$.costPrice").doesNotExist())
            .andExpect(jsonPath("$.marginPercent").doesNotExist());

        mvc.perform(put("/api/products/{id}", productId)
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hack\",\"categoryId\":\"" + categoryId + "\",\"costPrice\":0,\"salePrice\":1,\"stockControlled\":false}"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/products/{id}", productId).header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void productRequiresCategory() throws Exception {
        mvc.perform(post("/api/products")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sem categoria\",\"costPrice\":10,\"salePrice\":12,\"stockControlled\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void categoryCannotBeDeletedWhileProductIsAttachedAndCanBeReordered() throws Exception {
        String first = createCategory(adminToken, "Primeira");
        String second = createCategory(adminToken, "Segunda");
        createProduct(adminToken, first, "Produto", "10.00", "12.00");

        mvc.perform(patch("/api/categories/reorder")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"" + second + "\",\"" + first + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(second))
            .andExpect(jsonPath("$[0].order").value(0));

        mvc.perform(delete("/api/categories/{id}", first).header("Authorization", bearer(adminToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    @Test
    void clientProductPriceUsesOverrideThenFallsBackToProductPrice() throws Exception {
        String clientId = createClient(adminToken, "Preço Especial");
        String productId = createProduct(adminToken, null, "Produto Preço", "50.00", "70.00");

        mvc.perform(get("/api/clients/{clientId}/product-prices/{productId}", clientId, productId)
                .header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(70.00))
            .andExpect(jsonPath("$.custom").value(false));

        mvc.perform(put("/api/clients/{clientId}/product-prices/{productId}", clientId, productId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":65.50}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(65.50))
            .andExpect(jsonPath("$.custom").value(true));

        mvc.perform(put("/api/clients/{clientId}/product-prices/{productId}", clientId, productId)
                .header("Authorization", bearer(sellerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1.00}"))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/clients/{clientId}/product-prices/{productId}", clientId, productId)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/clients/{clientId}/product-prices/{productId}", clientId, productId)
                .header("Authorization", bearer(sellerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(70.00))
            .andExpect(jsonPath("$.custom").value(false));
    }

    @Test
    void adminUploadsProductPhotoAndSellerCannotUploadMedia() throws Exception {
        String productId = createProduct(adminToken, null, "Produto Foto", "10.00", "12.00");
        MockMultipartFile image = new MockMultipartFile(
            "file", "produto.png", "image/png", "png-content".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/products/{id}/photos", productId)
                .file(image)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://cdn.example.com/empresas/")));

        mvc.perform(multipart("/api/products/{id}/photos", productId)
                .file(image)
                .header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUploadCompanyLogoButSellerCannot() throws Exception {
        MockMultipartFile logo = new MockMultipartFile(
            "file", "logo.webp", "image/webp", "webp-content".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/company/logo")
                .file(logo)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://cdn.example.com/empresas/")));

        mvc.perform(multipart("/api/company/logo")
                .file(logo)
                .header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());
    }

    private String createClient(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/clients")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"phone\":\"51999999999\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return jsonString(body, "id");
    }

    private String createCategory(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/categories")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return jsonString(body, "id");
    }

    private String createProduct(String token, String categoryId, String name, String cost, String sale) throws Exception {
        if (categoryId == null) {
            categoryId = createCategory(token, "Auto " + java.util.UUID.randomUUID());
        }
        String category = "\"" + categoryId + "\"";
        String body = mvc.perform(post("/api/products")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"categoryId\":" + category +
                    ",\"costPrice\":" + cost + ",\"salePrice\":" + sale + ",\"stockControlled\":false}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return jsonString(body, "id");
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "192.0.2." + LOGIN_IP_SEQUENCE.getAndIncrement())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Senha123\"}"))
            .andReturn().getResponse().getContentAsString();
        return jsonString(body, "accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String jsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
