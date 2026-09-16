package com.gestordevendas.api.transaction;

import com.gestordevendas.api.client.Client;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditQueryIntegrationTest extends BusinessIntegrationTestSupport {

    @Test
    void ownerCanReadOwnTenantAuditButSellerCannotAndOtherTenantDoesNotSeeIt() throws Exception {
        Client client = createClient("Auditado");
        mvc.perform(post("/api/clients/{id}/order-link/regenerate", client.getId())
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());

        mvc.perform(get("/api/audit")
                .param("action", "CLIENT_ORDER_LINK_REGENERATED")
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("CLIENT_ORDER_LINK_REGENERATED"))
            .andExpect(jsonPath("$[0].entityId").value(client.getId().toString()));

        mvc.perform(get("/api/audit").header("Authorization", bearer(sellerToken)))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/audit")
                .param("action", "CLIENT_ORDER_LINK_REGENERATED")
                .header("Authorization", bearer(otherOwnerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
