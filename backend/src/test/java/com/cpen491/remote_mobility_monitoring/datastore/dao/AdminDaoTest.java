package com.cpen491.remote_mobility_monitoring.datastore.dao;

import com.cpen491.remote_mobility_monitoring.datastore.exception.DuplicateRecordException;
import com.cpen491.remote_mobility_monitoring.datastore.exception.RecordDoesNotExistException;
import com.cpen491.remote_mobility_monitoring.datastore.model.Admin;
import com.cpen491.remote_mobility_monitoring.datastore.model.Organization;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.cpen491.remote_mobility_monitoring.datastore.model.Const.AdminTable;
import static com.cpen491.remote_mobility_monitoring.datastore.model.Const.OrganizationTable;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.ADMIN_RECORD_NULL_ERROR_MESSAGE;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.EMAIL_BLANK_ERROR_MESSAGE;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.FIRST_NAME_BLANK_ERROR_MESSAGE;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.ID_BLANK_ERROR_MESSAGE;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.LAST_NAME_BLANK_ERROR_MESSAGE;
import static com.cpen491.remote_mobility_monitoring.dependency.utility.Validator.ORGANIZATION_ID_BLANK_ERROR_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDaoTest extends DaoTestParent {
    private static final String ID = "admin-id-123";
    private static final String EMAIL1 = "johnsmith@email.com";
    private static final String EMAIL2 = "johnsmithiscool@email.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Smith";
    private static final String EXISTS_ORGANIZATION_ID = "org-id-abc";
    private static final String NOT_EXISTS_ORGANIZATION_ID = "org-id-not";

    DynamoDbTable<Organization> organizationTable;
    DynamoDbTable<Admin> table;
    AdminDao cut;

    @BeforeEach
    public void setup() {
        setupOrganizationTable();
        setupAdminTable();

        organizationTable = ddbEnhancedClient.table(OrganizationTable.TABLE_NAME, TableSchema.fromBean(Organization.class));
        Map<String, DynamoDbIndex<Organization>> organizationIndexMap = new HashMap<>();
        organizationIndexMap.put(OrganizationTable.NAME_INDEX_NAME, organizationTable.index(OrganizationTable.NAME_INDEX_NAME));
        OrganizationDao organizationDao = new OrganizationDao(new GenericDao<>(organizationTable, organizationIndexMap, ddbEnhancedClient));

        table = ddbEnhancedClient.table(AdminTable.TABLE_NAME, TableSchema.fromBean(Admin.class));
        Map<String, DynamoDbIndex<Admin>> adminIndexMap = new HashMap<>();
        for (Pair<String, String> indexNameAndKey : AdminTable.INDEX_NAMES_AND_KEYS) {
            String indexName = indexNameAndKey.getLeft();
            adminIndexMap.put(indexName, table.index(indexName));
        }
        cut = new AdminDao(new GenericDao<>(table, adminIndexMap, ddbEnhancedClient), organizationDao);

        Organization organization = Organization.builder().id(EXISTS_ORGANIZATION_ID).build();
        organizationTable.putItem(organization);
    }

    @AfterEach
    public void teardown() {
        teardownOrganizationTable();
        teardownAdminTable();
    }

    @Test
    public void testCreate_HappyCase() {
        Admin newRecord = buildAdmin();
        cut.create(newRecord);

        assertNotEquals(ID, newRecord.getId());
        assertEquals(EMAIL1, newRecord.getEmail());
        assertEquals(FIRST_NAME, newRecord.getFirstName());
        assertEquals(LAST_NAME, newRecord.getLastName());
        assertEquals(EXISTS_ORGANIZATION_ID, newRecord.getOrganizationId());
        assertNotNull(newRecord.getCreatedAt());
        assertNotNull(newRecord.getUpdatedAt());
    }

    @Test
    public void testCreate_WHEN_OrganizationDoesNotExist_THEN_ThrowRecordDoesNotExistException() {
        Admin newRecord = buildAdmin();
        newRecord.setOrganizationId(NOT_EXISTS_ORGANIZATION_ID);
        assertThatThrownBy(() -> cut.create(newRecord)).isInstanceOf(RecordDoesNotExistException.class);
    }

    @Test
    public void testCreate_WHEN_RecordWithEmailAlreadyExists_THEN_ThrowDuplicateRecordException() {
        Admin newRecord = buildAdmin();
        cut.create(newRecord);
        assertThatThrownBy(() -> cut.create(newRecord)).isInstanceOf(DuplicateRecordException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidInputsForCreate")
    public void testCreate_WHEN_InvalidInput_THEN_ThrowInvalidInputException(Admin record, String errorMessage) {
        assertInvalidInputExceptionThrown(() -> cut.create(record), errorMessage);
    }

    private static Stream<Arguments> invalidInputsForCreate() {
        return Stream.of(
                Arguments.of(null, ADMIN_RECORD_NULL_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, null, FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), EMAIL_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, "", FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), EMAIL_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, null, LAST_NAME, EXISTS_ORGANIZATION_ID), FIRST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, "", LAST_NAME, EXISTS_ORGANIZATION_ID), FIRST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, null, EXISTS_ORGANIZATION_ID), LAST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, "", EXISTS_ORGANIZATION_ID), LAST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, LAST_NAME, null), ORGANIZATION_ID_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, LAST_NAME, ""), ORGANIZATION_ID_BLANK_ERROR_MESSAGE)
        );
    }

    @Test
    public void testFindById_HappyCase() {
        Admin newRecord = buildAdmin();
        table.putItem(newRecord);

        Admin record = cut.findById(ID);
        assertEquals(newRecord, record);
    }

    @Test
    public void testFindById_WHEN_RecordDoesNotExist_THEN_ReturnNull() {
        Admin record = cut.findById(ID);
        assertNull(record);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindById_WHEN_InvalidInput_THEN_ThrowInvalidInputException(String id) {
        assertInvalidInputExceptionThrown(() -> cut.findById(id), ID_BLANK_ERROR_MESSAGE);
    }

    @Test
    public void testFindByEmail_HappyCase() {
        Admin newRecord = buildAdmin();
        table.putItem(newRecord);

        Admin record = cut.findByEmail(EMAIL1);
        assertEquals(newRecord, record);
    }

    @Test
    public void testFindByEmail_WHEN_RecordDoesNotExist_THEN_ReturnNull() {
        Admin record = cut.findByEmail(EMAIL1);
        assertNull(record);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindByEmail_WHEN_InvalidInput_THEN_ThrowInvalidInputException(String email) {
        assertInvalidInputExceptionThrown(() -> cut.findByEmail(email), EMAIL_BLANK_ERROR_MESSAGE);
    }

    @Test
    public void testFindAllInOrganization_HappyCase() {
        Admin newRecord1 = buildAdmin();
        cut.create(newRecord1);
        Admin newRecord2 = buildAdmin();
        newRecord2.setEmail(EMAIL2);
        cut.create(newRecord2);

        Iterator<Page<Admin>> iterator = cut.findAllInOrganization(EXISTS_ORGANIZATION_ID);
        assertTrue(iterator.hasNext());
        List<Admin> admins = iterator.next().items();
        assertThat(admins).containsExactlyInAnyOrder(newRecord1, newRecord2);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testFindAllInOrganization_WHEN_RecordsDoNotExist_THEN_ReturnIteratorWithEmptyPage() {
        Iterator<Page<Admin>> iterator = cut.findAllInOrganization(EXISTS_ORGANIZATION_ID);
        assertTrue(iterator.hasNext());
        List<Admin> admins = iterator.next().items();
        assertThat(admins).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindAllInOrganization_WHEN_InvalidInput_THEN_ThrowInvalidInputException(String organizationId) {
        assertInvalidInputExceptionThrown(() -> cut.findAllInOrganization(organizationId), ORGANIZATION_ID_BLANK_ERROR_MESSAGE);
    }

    @Test
    public void testUpdate_HappyCase() {
        Admin newRecord = buildAdmin();
        cut.create(newRecord);

        Admin updatedRecord = cut.findById(newRecord.getId());
        assertEquals(newRecord, updatedRecord);
        updatedRecord.setEmail(EMAIL2);
        cut.update(updatedRecord);

        Admin found = cut.findById(newRecord.getId());
        assertEquals(newRecord.getId(), found.getId());
        assertNotEquals(newRecord.getEmail(), found.getEmail());
        assertNotEquals(newRecord.getUpdatedAt(), found.getUpdatedAt());
        assertEquals(newRecord.getCreatedAt(), found.getCreatedAt());
    }

    @Test
    public void testUpdate_WHEN_RecordDoesNotExist_THEN_ThrowRecordDoesNotExistException() {
        Admin newRecord = buildAdmin();
        assertThatThrownBy(() -> cut.update(newRecord)).isInstanceOf(RecordDoesNotExistException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidInputsForUpdate")
    public void testUpdate_WHEN_InvalidInput_THEN_ThrowInvalidInputException(Admin record, String errorMessage) {
        assertInvalidInputExceptionThrown(() -> cut.update(record), errorMessage);
    }

    private static Stream<Arguments> invalidInputsForUpdate() {
        return Stream.of(
                Arguments.of(null, ADMIN_RECORD_NULL_ERROR_MESSAGE),
                Arguments.of(buildAdmin(null, EMAIL1, FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), ID_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin("", EMAIL1, FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), ID_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, null, FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), EMAIL_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, "", FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID), EMAIL_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, null, LAST_NAME, EXISTS_ORGANIZATION_ID), FIRST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, "", LAST_NAME, EXISTS_ORGANIZATION_ID), FIRST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, null, EXISTS_ORGANIZATION_ID), LAST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, "", EXISTS_ORGANIZATION_ID), LAST_NAME_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, LAST_NAME, null), ORGANIZATION_ID_BLANK_ERROR_MESSAGE),
                Arguments.of(buildAdmin(ID, EMAIL1, FIRST_NAME, LAST_NAME, ""), ORGANIZATION_ID_BLANK_ERROR_MESSAGE)
        );
    }

    @Test
    public void testDelete_HappyCase() {
        Admin newRecord = buildAdmin();
        table.putItem(newRecord);
        Admin found = table.getItem(newRecord);
        assertNotNull(found);

        cut.delete(ID);
        found = table.getItem(newRecord);
        assertNull(found);
    }

    @Test
    public void testDelete_WHEN_RecordDoesNotExist_THEN_DoNothing() {
        assertDoesNotThrow(() -> cut.delete(ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testDelete_WHEN_InvalidInput_THEN_ThrowInvalidInputException(String id) {
        assertInvalidInputExceptionThrown(() -> cut.delete(id), ID_BLANK_ERROR_MESSAGE);
    }

    private static Admin buildAdmin() {
        return buildAdmin(ID, EMAIL1, FIRST_NAME, LAST_NAME, EXISTS_ORGANIZATION_ID);
    }

    private static Admin buildAdmin(String id, String email, String firstName, String lastName, String organizationId) {
        return Admin.builder()
                .id(id)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .organizationId(organizationId)
                .build();
    }
}
