package org.thomcgn.backend.table.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.table.api.dto.TableRequest;
import org.thomcgn.backend.table.api.dto.TableResponse;
import org.thomcgn.backend.table.domain.TableEntity;
import org.thomcgn.backend.table.repository.TableRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> list() {
        return tableRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TableResponse create(TableRequest request) {
        TableEntity table = new TableEntity();
        apply(table, request);
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request) {
        TableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Table not found: " + id));
        apply(table, request);
        return toResponse(tableRepository.save(table));
    }

    private void apply(TableEntity table, TableRequest request) {
        table.setName(request.name().trim());
        table.setCapacity(request.capacity());
        table.setArea(request.area());
        table.setStatus(request.status());
        table.setActive(request.active());
    }

    private TableResponse toResponse(TableEntity table) {
        return new TableResponse(table.getId(), table.getName(), table.getCapacity(), table.getArea(), table.getStatus(), table.isActive());
    }
}

