package org.example.storevn.service;

import org.example.storevn.catalog.AdminSkyCatalogClient;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanCatalogServiceTest {

    @Mock
    private AdminSkyCatalogClient catalogClient;

    @InjectMocks
    private PlanCatalogService service;

    @Test
    void getActivePlans_delegatesFiltersAndSortToCatalogClient() {
        List<StorePlanResponse> plans = List.of(StorePlanResponse.builder().code("SKY89").build());
        when(catalogClient.findActivePlans(12, "desc")).thenReturn(plans);

        assertThat(service.getActivePlans(12, "desc")).isSameAs(plans);
        verify(catalogClient).findActivePlans(12, "desc");
    }

    @Test
    void getActivePlanByCode_returnsCatalogPlan() {
        StorePlanResponse plan = StorePlanResponse.builder().code("SKY89").build();
        when(catalogClient.findActiveByCode("SKY89")).thenReturn(Optional.of(plan));

        assertThat(service.getActivePlanByCode("SKY89")).isSameAs(plan);
    }

    @Test
    void getActivePlanByCode_throwsPlanNotFoundWhenCatalogHasNoActiveMatch() {
        when(catalogClient.findActiveByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActivePlanByCode("UNKNOWN"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }
}
