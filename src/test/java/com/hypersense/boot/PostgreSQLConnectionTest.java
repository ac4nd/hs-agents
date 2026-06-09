package com.hypersense.boot;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 数据库连接测试
 * <p>
 * 基于 application-local.yml 中的数据源配置，验证 PostgreSQL 连接的可用性。
 *
 * @author test
 */
class PostgreSQLConnectionTest {

    private static final String JDBC_URL = "jdbc:postgresql://47.107.160.31:5432/godlikeagents?currentSchema=public&stringtype=unspecified";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "123456";

    private DruidDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DruidDataSource();
        dataSource.setUrl(JDBC_URL);
        dataSource.setUsername(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setInitialSize(2);
        dataSource.setMinIdle(2);
        dataSource.setMaxActive(5);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestWhileIdle(true);
        dataSource.setTimeBetweenEvictionRunsMillis(60000);
        dataSource.setMinEvictableIdleTimeMillis(300000);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ======================== JDBC 直连测试 ========================

    @Test
    @DisplayName("JDBC 直连 - 验证 PostgreSQL 连接可用")
    void testDirectJdbcConnection() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            assertNotNull(conn, "连接不应为 null");
            assertFalse(conn.isClosed(), "连接应处于打开状态");
        }
    }

    @Test
    @DisplayName("JDBC 直连 - 验证连接元数据")
    void testConnectionMetadata() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            DatabaseMetaData meta = conn.getMetaData();

            assertEquals("PostgreSQL", meta.getDatabaseProductName(), "数据库类型应为 PostgreSQL");
            assertNotNull(meta.getDatabaseProductVersion(), "数据库版本不应为 null");

            System.out.println("数据库产品: " + meta.getDatabaseProductName());
            System.out.println("数据库版本: " + meta.getDatabaseProductVersion());
            System.out.println("驱动版本: " + meta.getDriverVersion());
        }
    }

    @Test
    @DisplayName("JDBC 直连 - 验证当前数据库和 Schema")
    void testDatabaseAndSchema() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            assertEquals("godlikeagents", catalog, "当前数据库应为 godlikeagents");
            assertEquals("public", schema, "当前 Schema 应为 public");
        }
    }

    // ======================== Druid 连接池测试 ========================

    @Test
    @DisplayName("Druid 连接池 - 验证数据源初始化")
    void testDruidDataSourceInit() throws SQLException {
        dataSource.init();

        assertTrue(dataSource.isInited(), "Druid 数据源应已初始化");
        assertEquals(2, dataSource.getInitialSize(), "初始连接数应匹配配置");
    }

    @Test
    @DisplayName("Druid 连接池 - 验证连接获取与归还")
    void testDruidGetAndReturnConnection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn, "从连接池获取的连接不应为 null");
            assertFalse(conn.isClosed(), "连接应处于打开状态");

            // 验证连接有效性
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next(), "查询应返回结果");
                assertEquals(1, rs.getInt(1), "SELECT 1 应返回 1");
            }
        }
    }

    @Test
    @DisplayName("Druid 连接池 - 验证多连接并发获取")
    void testDruidMultipleConnections() throws SQLException {
        int poolSize = 3;
        Connection[] connections = new Connection[poolSize];

        // 获取多个连接
        for (int i = 0; i < poolSize; i++) {
            connections[i] = dataSource.getConnection();
            assertNotNull(connections[i], "第 " + (i + 1) + " 个连接不应为 null");
        }

        // 归还所有连接
        for (int i = 0; i < poolSize; i++) {
            connections[i].close();
        }

        // 验证活跃连接数归零
        assertEquals(0, dataSource.getActiveCount(), "所有连接归还后活跃数应为 0");
    }

    @Test
    @DisplayName("Druid 连接池 - 验证验证查询")
    void testDruidValidationQuery() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    // ======================== 表结构验证测试 ========================

    @Test
    @DisplayName("Schema 验证 - 确认核心系统表存在")
    void testSystemTablesExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            String[] expectedTables = {"sys_user", "sys_menu", "sys_role", "sys_tenant"};
            for (String table : expectedTables) {
                boolean found = isTableExists(meta, table);
                assertTrue(found, "系统表 " + table + " 应存在");
                System.out.println("表 " + table + " 验证通过");
            }
        }
    }

    @Test
    @DisplayName("Schema 验证 - 查询 public Schema 下的表数量")
    void testTableCountInSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")) {

            assertTrue(rs.next());
            int tableCount = rs.getInt(1);
            System.out.println("public Schema 下共有 " + tableCount + " 张表");
            assertTrue(tableCount > 0, "public Schema 下应至少存在一张表");
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 检查指定表是否存在于当前数据库中
     */
    private boolean isTableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, "public", tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
