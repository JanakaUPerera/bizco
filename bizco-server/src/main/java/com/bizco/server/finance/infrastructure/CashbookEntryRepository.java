package com.bizco.server.finance.infrastructure;

import com.bizco.server.finance.domain.CashbookEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbookEntryRepository extends JpaRepository<CashbookEntry, UUID> {
}
