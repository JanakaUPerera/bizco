package com.bizco.server.finance.infrastructure;

import com.bizco.server.finance.domain.CreditNoteApplication;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteApplicationRepository extends JpaRepository<CreditNoteApplication, UUID> {
}
