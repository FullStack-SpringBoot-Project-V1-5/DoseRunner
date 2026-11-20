package tn.rnu.eniso.doserunner.order.facade;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageView {
        content = List.copyOf(content);
    }

    static <S, T> PageView<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageView<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
