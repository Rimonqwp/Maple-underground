package maple.underground_state.professions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionStoreTest {
    @TempDir Path directory;
    // Real JDBC persistence with SQLite; translate only the two MySQL upsert spellings.
    // Deployment still requires a separate real MySQL/Paper smoke test.
    private Connection connection() throws SQLException {
        Connection actual=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("progress.db"));
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
            if(method.getName().equals("prepareStatement") && args[0] instanceof String sql)args[0]=sql.replace("INSERT IGNORE","INSERT OR IGNORE")
                    .replace("ON DUPLICATE KEY UPDATE amount=VALUES(amount)","ON CONFLICT(uuid,progress_key) DO UPDATE SET amount=excluded.amount");
            try { return method.invoke(actual,args); } catch(InvocationTargetException e) { throw e.getCause(); }
        });
    }
    @Test void countersRemaindersChallengeLedgerAndUniqueBookSurviveReload() {
        UUID id=UUID.randomUUID();var store=new ProfessionStore(Logger.getAnonymousLogger(),this::connection);store.load(id);
        assertTrue(store.ready(id));store.set(id,"FARMING:xp_remainder",9876);store.set(id,"MINING:rare_misses",149);
        store.set(id,"MINING:elite_cooldown",1234567890123L);store.set(id,"MINING:highest_rewarded",55);store.add(id,"MINING:elite_kills",30);
        store.set(id,"book:mining_rare_2_helmet",1);store.set(id,"book:mining_rare_2_helmet",1);store.save(id);
        var reloaded=new ProfessionStore(Logger.getAnonymousLogger(),this::connection);reloaded.load(id);
        assertEquals(9876,reloaded.get(id,"FARMING:xp_remainder"));assertEquals(149,reloaded.get(id,"MINING:rare_misses"));
        assertEquals(1234567890123L,reloaded.get(id,"MINING:elite_cooldown"));assertEquals(55,reloaded.get(id,"MINING:highest_rewarded"));
        assertEquals(30,reloaded.get(id,"MINING:elite_kills"));assertEquals(1,reloaded.discoveries(id).size());
        reloaded.set(id,"MINING:rare_misses",0);reloaded.save(id);
        var third=new ProfessionStore(Logger.getAnonymousLogger(),this::connection);third.load(id);assertEquals(0,third.get(id,"MINING:rare_misses"));
        assertEquals(1,third.discoveries(id).size());
    }
}
