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
public class TwoCollectionSync_Test extends AndroidTestCase {

    private LocalStorage storage;

    public void setUp() {
        storage = new LocalStorage(getContext(), true);
        BaasBox.Builder b = new BaasBox.Builder(getContext());
        BaasBox client = b.setApiDomain("192.168.56.1")
                .setAppCode("1234567890").setPort(9000)
                .init();
        BaasUser user = BaasUser.withUserName("simo")
                .setPassword("simo");
        user.login(new BaasHandler<BaasUser>() {
            @Override
            public void handle(BaasResult<BaasUser> result) {
                if (result.isSuccess()) {
                    Log.d("LOG", "The user is currently logged in: " + result.value());
                } else {
                    Log.e("LOG", "Show error", result.error());
                }
            }
        });

    }

    /**
     * Tests the Synchronization of a use-case situation with the Server  after two previous Synchronizations, so that to test the correct
     * use of the variable lastSyncDate in AbstractPolicy class
     * The first sync manages a conflict (AS vs AC) won by the server according to our default policy
     * The second sync synchronizes with the Server a local creation and a local update
     * The third sync manages a conflict (AS vs CC) won by the client according to our default policy
     */
    public void test() throws IOException, SyncException {
        storage.deleteAllDocuments();
        /* creo un record sul server e sul client (collection memos) */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testMemos");
        jsonObject.putString("content", "contenuto");
        storage.create("memos", jsonObject);

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

        /* creo un record sul server e sul client (collection test) */
        JsonObject jsonObject_1 = new JsonObject();
        jsonObject_1.putString("title", "testTest");
        jsonObject_1.putString("content", "contenuto");
        storage.create("test", jsonObject_1);
        BaasResult<JsonObject> postResponse_1 = baasBox.restSync(HttpRequest.POST, "document/test", jsonObject_1, true);
        assertTrue(postResponse_1.isSuccess());
        String idServer_1 = null;
        Wait.sleep2seconds();
        try {
            idServer_1 = postResponse_1.get().getObject("data").getString("id");
        } catch (BaasException e) {
            e.printStackTrace();
        }
        assertTrue(idServer_1!=null);
        List<JsonObject> dirtyObjects_1 = storage.getDirtyCreatedDocuments();
        JsonObject json = new JsonObject();
        for(JsonObject o : dirtyObjects_1){
            if(o.getObject("data").getString("@class").equals("test"))
                json=o;
        }
        assertTrue(json.size()!=0);
        try {
            storage.updateAfterCreation(json, postResponse_1.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }


        Wait.sleep2seconds();

        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)
        * altrimenti executeSyncAll(...) non esegue il primo passo poichè la last_sync_date sarebbe null */
        storage.saveSyncDate(LocalStorage.generateNewDateAsString());

        Wait.sleep2seconds();

        /* modifico il primo oggetto sul server */
        JsonObject jsonUpdate = new JsonObject();
        jsonUpdate.putString("title", "testMemosUpdatedServer");
        jsonUpdate.putString("content", "contenuto");
        BaasResult<JsonObject> putResponse = baasBox.restSync(HttpRequest.PUT, "document/memos/" + idServer,
                jsonUpdate, true);
        assertTrue(putResponse.isSuccess());
        try {
            assertTrue(putResponse.get().getObject("data").getString("title").equals("testMemosUpdatedServer"));
        } catch (BaasException e) {
            e.printStackTrace();
        }
        /* lo modifico il primo oggetto sul client */
        storage.update(idServer, "memos", "title", "testMemosUpdatedClient");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("testMemosUpdatedClient"));

        /* modifico il secondo oggetto sul server */
        JsonObject jsonUpdate_1 = new JsonObject();
        jsonUpdate_1.putString("title", "testTestUpdatedServer");
        jsonUpdate_1.putString("content", "contenuto");
        BaasResult<JsonObject> putResponse_1 = baasBox.restSync(HttpRequest.PUT, "document/test/" + idServer_1,
                jsonUpdate_1, true);
        assertTrue(putResponse_1.isSuccess());
        try {
            assertTrue(putResponse_1.get().getObject("data").getString("title").equals("testTestUpdatedServer"));
        } catch (BaasException e) {
            e.printStackTrace();
        }
        /* lo modifico il secondo oggetto sul client */
        storage.update(idServer_1, "test", "title", "testTestUpdatedClient");
        assertTrue(storage._getByIDWithoutCollection(idServer_1).getObject("data").getString("title").equals("testTestUpdatedClient"));



        Wait.sleep2seconds();

        /* devo fare il login con un altro utente (che effettuerà l'eliminazione sul proprio client)
        * altrimenti il docuemnto creato precedentemente viene escluso dalla GET al server (perchè avrebbe session token uguale) */

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

        /* faccio la prima sync (vince il server in entrambi i casi) */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_last_sync_date") != null);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty") == 0);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("testMemosUpdatedServer"));
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("content").equals("contenuto"));

        assertTrue(storage._getByIDWithoutCollection(idServer_1).getObject("local_data").getString("_last_sync_date") != null);
        assertTrue(storage._getByIDWithoutCollection(idServer_1).getObject("local_data").getLong("_dirty") == 0);
        assertTrue(storage._getByIDWithoutCollection(idServer_1).getObject("data").getString("title").equals("testTestUpdatedServer"));
        assertTrue(storage._getByIDWithoutCollection(idServer_1).getObject("data").getString("content").equals("contenuto"));
    }
}