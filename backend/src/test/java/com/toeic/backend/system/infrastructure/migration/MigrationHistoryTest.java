package com.toeic.backend.system.infrastructure.migration;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MigrationHistoryTest {
    @Test void failedHistoryRowIsRejected() {
        var row=new MigrationHistory.Row(1,"1","V1__initial_schema.sql","initial schema","SQL",1,false);
        assertThrows(IllegalStateException.class,()->MigrationHistory.verifyPrefix(List.of(row)));
    }
    @Test void duplicateExactV34RowsAreRejected() {
        // Pure prefix-validator input; never creates or edits a database history.
        var rows=new ArrayList<MigrationHistory.Row>();
        for(String version:MigrationHistory.VERSIONS) {
            rows.add(new MigrationHistory.Row(rows.size()+1,version,"script","description","SQL",1,true));
            if(version.equals("34"))break;
        }
        rows.add(new MigrationHistory.Row(rows.size()+1,"34","V34__managed_system_defaults.sql","managed system defaults","SQL",1299604777,true));
        assertThrows(IllegalStateException.class,()->MigrationHistory.verifyPrefix(rows));
    }
}
