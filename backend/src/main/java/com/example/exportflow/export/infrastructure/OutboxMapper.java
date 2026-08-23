package com.example.exportflow.export.infrastructure;

import com.example.exportflow.export.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxMapper {
    int insert(@Param("eventId") String eventId, @Param("aggregateId") long aggregateId,
               @Param("eventType") String eventType, @Param("payload") String payload,
               @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("createdAt") LocalDateTime createdAt);
    List<OutboxEvent> findPending(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int markPublished(@Param("id") long id, @Param("publishedAt") LocalDateTime publishedAt);
    int markFailed(@Param("id") long id, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("lastError") String lastError);
}
