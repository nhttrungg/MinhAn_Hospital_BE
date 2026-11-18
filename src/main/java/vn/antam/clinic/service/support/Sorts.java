package vn.antam.clinic.service.support;

import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.web.rest.errors.ApiException;

public final class Sorts {

    private Sorts() {}

    public static Pageable allow(Pageable pageable, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Sắp xếp không hợp lệ",
                    "Không hỗ trợ sắp xếp theo trường " + order.getProperty() + "."
                );
            }
        }
        return pageable;
    }
}
