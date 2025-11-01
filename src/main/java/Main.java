import com.chat.server.Server;
import com.chat.utils.DatabaseManager;

// 程序入口：初始化数据库连接池并启动服务器
public class Main {
    // 主函数：启动服务器（未使用命令行参数）
    public static void main(String[] args) {
        // JVM 退出时关闭连接池
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::shutdown));

        // 启动前进行数据库连通性检查
        try (var connection = DatabaseManager.getConnection()) {
            // 成功则不额外输出
        } catch (Exception e) {
            System.err.println("初始化数据库连接池失败，程序退出。");
            return; // Exit if DB connection fails
        }

        Server server = new Server(12345);
        server.start();
    }
}
