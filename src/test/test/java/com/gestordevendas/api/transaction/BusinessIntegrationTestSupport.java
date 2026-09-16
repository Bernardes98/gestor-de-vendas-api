package com.gestordevendas.api.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestordevendas.api.auth.JwtService;
import com.gestordevendas.api.category.ProductCategory;
import com.gestordevendas.api.category.ProductCategoryRepository;
import com.gestordevendas.api.client.Client;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.product.Product;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
abstract class BusinessIntegrationTestSupport extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired ProductCategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    Company company;
    Company otherCompany;
    User owner;
    User admin;
    User seller;
    User otherOwner;
    String ownerToken;
    String adminToken;
    String sellerToken;
    String otherOwnerToken;
    ProductCategory category;

    @BeforeEach
    void createBusinessFixtures() {
        company = companyRepository.save(Company.create("phase3-a", "Phase 3 A", null, null, null, null));
        otherCompany = companyRepository.save(Company.create("phase3-b", "Phase 3 B", null, null, null, null));
        owner = saveUser("owner.phase3@example.com", company, CompanyRole.OWNER);
        admin = saveUser("admin.phase3@example.com", company, CompanyRole.ADMIN);
        seller = saveUser("seller.phase3@example.com", company, CompanyRole.VENDEDOR);
        otherOwner = saveUser("other.phase3@example.com", otherCompany, CompanyRole.OWNER);
        ownerToken = jwtService.issueAccessToken(owner);
        adminToken = jwtService.issueAccessToken(admin);
        sellerToken = jwtService.issueAccessToken(seller);
        otherOwnerToken = jwtService.issueAccessToken(otherOwner);
        category = categoryRepository.save(ProductCategory.create(company, "Geral", 0));
    }

    Product createProduct(String name, BigDecimal cost, BigDecimal sale, boolean stockControlled) {
        Product product = Product.create(company, name, sale);
        product.update(name, null, null, category, cost, sale, stockControlled);
        return productRepository.save(product);
    }

    Product createOtherCompanyProduct(String name, BigDecimal cost, BigDecimal sale, boolean stockControlled) {
        ProductCategory otherCategory = categoryRepository.save(ProductCategory.create(otherCompany, "Geral B", 0));
        Product product = Product.create(otherCompany, name, sale);
        product.update(name, null, null, otherCategory, cost, sale, stockControlled);
        return productRepository.save(product);
    }

    Client createClient(String name) {
        return clientRepository.save(Client.create(company, name));
    }

    String createPurchase(String token, Product product, String quantity, String unitCost) throws Exception {
        String body = mvc.perform(post("/api/purchases")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity +
                    ",\"unitCost\":" + unitCost + "}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json(body).get("id").asText();
    }

    String createSale(String token, Product product, String quantity, String unitPrice, String paymentType) throws Exception {
        String body = mvc.perform(post("/api/sales")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"" + paymentType + "\",\"items\":[{\"productId\":\"" + product.getId() +
                    "\",\"quantity\":" + quantity + ",\"unitPrice\":" + unitPrice + "}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json(body).get("id").asText();
    }

    JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    String bearer(String token) {
        return "Bearer " + token;
    }

    private User saveUser(String email, Company targetCompany, CompanyRole role) {
        User user = userRepository.save(User.create(email, email, passwordEncoder.encode("Senha123")));
        membershipRepository.save(CompanyMembership.create(targetCompany, user, role));
        return user;
    }
}
