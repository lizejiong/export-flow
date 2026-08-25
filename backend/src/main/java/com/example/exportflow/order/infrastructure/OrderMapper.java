package com.example.exportflow.order.infrastructure;

import com.example.exportflow.order.application.OrderFilter;
import com.example.exportflow.order.domain.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {
    List<Order> findPage(@Param("filter") OrderFilter filter, @Param("snapshotMaxId") Long snapshotMaxId,
                         @Param("offset") long offset, @Param("limit") int limit);
    long count(@Param("filter") OrderFilter filter, @Param("snapshotMaxId") Long snapshotMaxId);
    Long findMaxId();
    List<Long> findExistingIds(@Param("ids") List<Long> ids);
    List<Order> findExportBatch(@Param("filter") OrderFilter filter, @Param("snapshotMaxId") long snapshotMaxId,
                                @Param("afterId") long afterId, @Param("limit") int limit);
    List<Order> findSelectedBatch(@Param("taskId") long taskId, @Param("afterId") long afterId, @Param("limit") int limit);
    long countAll();
    int insertBatch(@Param("orders") List<Order> orders);
}
