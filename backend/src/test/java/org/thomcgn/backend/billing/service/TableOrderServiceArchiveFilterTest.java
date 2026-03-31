package org.thomcgn.backend.billing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thomcgn.backend.billing.api.dto.TableOrderResponse;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.menu.repository.VolumePriceRepository;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.table.repository.TableRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableOrderServiceArchiveFilterTest {

    @Mock
    private TableOrderRepository orderRepository;

    @Mock
    private TableOrderItemRepository itemRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DrinkVariantRepository drinkVariantRepository;

    @Mock
    private VolumePriceRepository volumePriceRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private TableOrderService tableOrderService;

    @Test
    void searchArchive_unpaidIgnoresDateRange() {
        when(orderRepository.searchUnpaidArchive(eq(TableOrderStatus.CLOSED), anyString())).thenReturn(List.of());

        tableOrderService.searchArchive(LocalDate.of(2035, 1, 1), "T1", "UNPAID");

        verify(orderRepository).searchUnpaidArchive(TableOrderStatus.CLOSED, "T1");
        verify(orderRepository, never()).searchArchive(eq(TableOrderStatus.CLOSED), any(LocalDateTime.class), any(LocalDateTime.class), any(), any());
    }

    @Test
    void searchArchive_unpaidHandlesCorruptRelationsWithout500() {
        TableOrder corruptOrder = new TableOrder();
        corruptOrder.setId(99L);
        corruptOrder.setStatus(TableOrderStatus.CLOSED);
        corruptOrder.setPaid(false);

        when(orderRepository.findAllByStatusAndPaidFalseOrderByClosedAtDesc(eq(TableOrderStatus.CLOSED))).thenReturn(List.of(corruptOrder));
        when(itemRepository.findByTableOrderId(99L)).thenReturn(List.of());
        when(itemRepository.getTotalByTableOrderId(99L)).thenReturn(java.math.BigDecimal.ZERO);

        List<TableOrderResponse> result = assertDoesNotThrow(() -> tableOrderService.searchArchive(null, null, "UNPAID"));

        assertEquals(1, result.size());
        assertEquals("Unbekannter Tisch", result.getFirst().tableName());
        verify(orderRepository).findAllByStatusAndPaidFalseOrderByClosedAtDesc(TableOrderStatus.CLOSED);
    }
}

