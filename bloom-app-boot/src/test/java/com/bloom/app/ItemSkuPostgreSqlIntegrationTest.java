package com.bloom.app;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.persistence.repository.ItemCategoryRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.ItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bloom.backup.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class ItemSkuPostgreSqlIntegrationTest {
    private static final String EXTERNAL_DATABASE_URL = System.getProperty("bloom.test.database.url");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgresWhenRequired();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ItemService itemService;
    @Autowired private ItemCategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ItemRepository itemRepository;

    private String categoryCode;
    private ItemResponse first;
    private ItemResponse second;
    private Long firstId;
    private Long secondId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            registry.add("spring.datasource.username",
                () -> System.getProperty("bloom.test.database.username", "postgres"));
            registry.add("spring.datasource.password",
                () -> System.getProperty("bloom.test.database.password", "postgres"));
        }
    }

    @BeforeEach
    void createIsolatedItems() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "ignored", List.of()));
        categoryCode = "SKU-" + UUID.randomUUID();
        categoryRepository.saveAndFlush(ItemCategory.builder()
            .code(categoryCode).name("SKU regression").active(true).build());
        first = itemService.createItem(request(categoryCode + "-A", false));
        second = itemService.createItem(request(categoryCode + "-B", true));
        firstId = itemRepository.findItemBySku(first.getSku()).orElseThrow().getId();
        secondId = itemRepository.findItemBySku(second.getSku()).orElseThrow().getId();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @Test
    void duplicateCreationReturnsSkuConflictWithoutChangingItemsOrOpeningStock() throws Exception {
        assertDuplicateCreation();
    }

    @Test
    void conflictingUpdateReturnsSkuConflictWithoutChangingEitherItemOrLedger() throws Exception {
        assertConflictingUpdate();
    }

    @Test
    void unchangedSkuUpdateSucceedsAndPreservesStockAndLedger() throws Exception {
        List<Map<String, Object>> firstBefore = itemRows(first.getSku());
        List<Map<String, Object>> ledgerBefore = ledgerRows();
        long countBefore = itemRepository.count();

        mockMvc.perform(put("/api/items/{sku}", second.getSku())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                    "sku", second.getSku(), "name", "Renamed cable", "price", 15000))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sku").value(second.getSku()))
            .andExpect(jsonPath("$.data.name").value("Renamed cable"));

        var updated = itemRepository.findItemBySku(second.getSku()).orElseThrow();
        assertThat(updated.getId()).isEqualTo(secondId);
        assertThat(updated.getName()).isEqualTo("Renamed cable");
        assertThat(updated.getPrice()).isEqualByComparingTo("15000");
        assertThat(updated.getStockStore()).isEqualByComparingTo("10");
        assertThat(updated.getStockWarehouse()).isEqualByComparingTo("20");
        assertThat(itemRows(first.getSku())).isEqualTo(firstBefore);
        assertThat(ledgerRows()).isEqualTo(ledgerBefore);
        assertThat(itemRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void databaseClosesCreateRaceAfterAvailabilityCheckWithoutOpeningMovements() throws Exception {
        // Simulate a stale precheck; the actual migrated PostgreSQL constraint must reject the write.
        doReturn(false).when(itemRepository).existsBySku(first.getSku());
        assertDuplicateCreation();
    }

    @Test
    void databaseClosesUpdateRaceAndRollsBackAllMappedChanges() throws Exception {
        doReturn(false).when(itemRepository).existsBySku(first.getSku());
        assertConflictingUpdate();
    }

    private void assertDuplicateCreation() throws Exception {
        List<Map<String, Object>> itemsBefore = itemRows(first.getSku(), second.getSku());
        List<Map<String, Object>> ledgerBefore = ledgerRows();
        long countBefore = itemRepository.count();
        CreateItemRequest duplicate = request(first.getSku(), false);
        duplicate.setName("Rejected duplicate");
        duplicate.setStockStore(new BigDecimal("7"));
        duplicate.setStockWarehouse(new BigDecimal("9"));

        expectSkuConflict(mockMvc.perform(post("/api/items")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(duplicate))));

        assertThat(itemRows(first.getSku(), second.getSku())).isEqualTo(itemsBefore);
        assertThat(ledgerRows()).isEqualTo(ledgerBefore);
        assertThat(itemRepository.count()).isEqualTo(countBefore);
    }

    private void assertConflictingUpdate() throws Exception {
        List<Map<String, Object>> itemsBefore = itemRows(first.getSku(), second.getSku());
        List<Map<String, Object>> ledgerBefore = ledgerRows();
        long countBefore = itemRepository.count();

        expectSkuConflict(mockMvc.perform(put("/api/items/{sku}", second.getSku())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of(
                "sku", first.getSku(), "name", "Rejected rename",
                "description", "Must roll back", "price", 999)))));

        assertThat(itemRows(first.getSku(), second.getSku())).isEqualTo(itemsBefore);
        assertThat(ledgerRows()).isEqualTo(ledgerBefore);
        assertThat(itemRepository.count()).isEqualTo(countBefore);
    }

    private void expectSkuConflict(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("item_sku_already_exists"))
            .andExpect(jsonPath("$.errorType").value("ItemSkuAlreadyExistsException"))
            .andExpect(jsonPath("$.field").value("sku"))
            .andExpect(jsonPath("$.rejectedValue").value(first.getSku()))
            .andExpect(jsonPath("$.message").value("Item SKU already exists: " + first.getSku()));
    }

    private List<Map<String, Object>> itemRows(String... skus) {
        return jdbc.queryForList("SELECT * FROM items WHERE sku IN ("
            + String.join(",", java.util.Collections.nCopies(skus.length, "?"))
            + ") ORDER BY id", (Object[]) skus);
    }

    private List<Map<String, Object>> ledgerRows() {
        return jdbc.queryForList(
            "SELECT * FROM stock_movements WHERE product_id IN (?, ?) ORDER BY id",
            firstId, secondId);
    }

    private CreateItemRequest request(String sku, boolean fractional) {
        return CreateItemRequest.builder()
            .categoryCode(categoryCode).sku(sku).name("SKU regression item")
            .price(new BigDecimal("12000"))
            .baseUnitOfMeasure(fractional ? UnitOfMeasure.METER : UnitOfMeasure.PIECE)
            .fractionalQuantityAllowed(fractional)
            .stockStore(BigDecimal.TEN).stockWarehouse(new BigDecimal("20"))
            .build();
    }

    private static PostgreSQLContainer<?> startPostgresWhenRequired() {
        if (EXTERNAL_DATABASE_URL != null) {
            return null;
        }
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
        postgres.start();
        return postgres;
    }
}
