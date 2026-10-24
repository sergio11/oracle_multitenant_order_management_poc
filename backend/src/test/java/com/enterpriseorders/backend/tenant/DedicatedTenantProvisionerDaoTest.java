package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.adapter.persistence.DedicatedTenantProvisionerDao;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirroring contract: the dedicated PDB must end up holding the same row
 * (and therefore the same identity id) as the shared PDB.
 */
@ExtendWith(MockitoExtension.class)
class DedicatedTenantProvisionerDaoTest {

    private static final NewTenantBO REQUEST =
            new NewTenantBO("NEWCO", "New Co", "ENTERPRISE", "SHARED_SCHEMA");

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement statement;
    @Mock private ResultSet resultSet;

    private DedicatedTenantProvisionerDao provisioner;

    @BeforeEach
    void setUp() throws SQLException {
        provisioner = new DedicatedTenantProvisionerDao(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void returnsExistingIdWithoutInsertingWhenTheRowIsAlreadyThere() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("tenant_id")).thenReturn(3L);

        assertEquals(3L, provisioner.ensureProvisioned(REQUEST));

        verify(connection, never()).prepareCall(anyString());
    }

    @Test
    void createsTheRowWhenTheDedicatedPdbDoesNotKnowItYet() throws Exception {
        when(resultSet.next()).thenReturn(false);
        CallableStatement cs = mock(CallableStatement.class);
        when(connection.prepareCall(anyString())).thenReturn(cs);
        when(cs.getBigDecimal(1)).thenReturn(BigDecimal.valueOf(7));

        assertEquals(7L, provisioner.ensureProvisioned(REQUEST));

        verify(cs).registerOutParameter(1, Types.NUMERIC);
        verify(cs).setString(2, "NEWCO");
        verify(cs).setString(4, "ENTERPRISE");
    }

    @Test
    void surfacesADuplicateAsTenantAlreadyExists() throws Exception {
        when(resultSet.next()).thenReturn(false);
        CallableStatement cs = mock(CallableStatement.class);
        when(connection.prepareCall(anyString())).thenReturn(cs);
        doThrow(new SQLException("ORA-20001: Tenant code already exists")).when(cs).execute();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provisioner.ensureProvisioned(REQUEST));

        assertEquals("TENANT_ALREADY_EXISTS", ex.getCode());
    }
}
