package syncTests;

import android.test.AndroidTestCase;
import android.util.Log;

import com.baasbox.android.BaasBox;
import com.baasbox.android.BaasException;
import com.baasbox.android.BaasHandler;
import com.baasbox.android.BaasResult;
import com.baasbox.android.BaasUser;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.net.HttpRequest;
import com.baasbox.deardiary.DearDiary;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import exceptions.SyncException;
import offlinesync.DefaultPolicy;
import offlinesync.LocalStorage;

/**
 * Created by Simone on 10/09/2014.
 */
public class SessionToken_Test extends AndroidTestCase {
    private LocalStorage storage;

    public void setUp() {
        storage = new LocalStorage(getContext(), true);
        BaasBox.Builder b = new BaasBox.Builder(getContext());
        BaasBox client = b.setApiDomain("192.168.56.1")
                .setAppCode("1234567890").setPort(9000)
                .init();
    }

    /**
     * Tests the Synchronization of a use-case situation with the Server  after two previous Synchronizations, so that to test the correct
     * use of the variable lastSyncDate in AbstractPolicy class
     * The first sync manages a conflict (AS vs AC) won by the server according to our default policy
     * The second sync synchronizes with the Server a local creation and a local update
     * The third sync manages a conflict (AS vs CC) won by the client according to our default policy
     */
    public void test() throws IOException, SyncException {
        BaasUser user = BaasUser.withUserName("simo")
                .setPassword("simo");
        user.login(new BaasHandler<BaasUser>() {
            @Override
            public void handle(BaasResult<BaasUser> result) {
                if (result.isSuccess()) {
                    Log.d("LOG", "The user is currently logged in: " + result.value());

                } else {
                    Log.e("LOG", "Show error", result.error());
                    throw new IllegalArgumentException("user not logged in");
                }
            }
        });
        Log.d("debug", "SESSION TOKEN SIMONE: " + BaasUser.current().getToken());

        Wait.sleep2seconds();

        storage.deleteAllDocuments();
        /* creo un record sul server e sul client */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testSessionToken");
        jsonObject.putString("content", "contenuto");
        DearDiary.getApp().getStorage().create("memos", jsonObject);

        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/memos", jsonObject, true);
        assertTrue(postResponse.isSuccess());
        List<JsonObject> dirtyObjects = storage.getDirtyCreatedDocuments();
        assertTrue(dirtyObjects.size() == 1);
        JsonObject jsonCreated = dirtyObjects.get(dirtyObjects.size() - 1);
        try {
            storage.updateAfterCreation(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")), postResponse.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }
        String idServer = storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getString("id");

        Wait.sleep2seconds();


        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)
        * altrimenti executeSyncAll(...) non esegue il primo passo poichè la last_sync_date sarebbe null */
        storage.saveSyncDate(LocalStorage.generateNewDateAsString());

        Wait.sleep2seconds();

        BaasUser.current().logout(new BaasHandler<Void>() {
            @Override
            public void handle(BaasResult<Void> result) {
                if(result.isSuccess()) {
                    Log.d("LOG", "Logged out: "+(BaasUser.current() == null));
                } else{
                    Log.e("LOG","Show error",result.error());
                    throw new IllegalArgumentException("user not logged in");
                }
            };
        });

        Wait.sleep2seconds();

        BaasUser user_1 = BaasUser.withUserName("andrea")
                .setPassword("andrea");
        user_1.login(new BaasHandler<BaasUser>() {
            @Override
            public void handle(BaasResult<BaasUser> result) {
                if (result.isSuccess()) {
                    Log.d("LOG", "The user is currently logged in: " + result.value());
                } else {
                    Log.e("LOG", "Show error", result.error());
                    throw new IllegalArgumentException("user not logged in");
                }
            }
        });

        Wait.sleep2seconds();

        /* lo modifico sul client */
        storage.update(idServer, "memos", "title", "titoloAggiornato");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloAggiornato"));

        /* faccio la prima sync (vince il client) */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_last_sync_date")!=null);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty")==0);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloAggiornato"));

        BaasResult<JsonObject> getResponse = baasBox.restSync(HttpRequest.GET, "document/memos/"+idServer,
                null, true);
        assertTrue(getResponse.isSuccess());
        try {
            assertTrue(getResponse.get().getObject("data").getString("title").equals("titoloAggiornato"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        Wait.sleep2seconds();

        /* lo modifico sul client */
        storage.update(idServer, "memos", "title", "titolo");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titolo"));

        /* faccio la seconda sync (mi aspetto che non ottenga niente dal server */
        defaultPolicy.executeSyncAll(storage);
        //osservo dall'output che non vi sono documenti restituiti dal server

        Wait.sleep2seconds();

        /* faccio la terza sync (mi aspetto che non ottenga niente dal server */
        defaultPolicy.executeSyncAll(storage);
    }
}
