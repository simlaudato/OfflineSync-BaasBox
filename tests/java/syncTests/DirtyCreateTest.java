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
 * Created by Simone on 31/07/2014.
 */
public class DirtyCreateTest extends AndroidTestCase {

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


    /** Tests the Synchronization with the Server after a local creation in the Storage
     * */
    public void test() throws IOException, SyncException {
        storage.deleteAllDocuments();
        /* faccio una creazione in locale */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testCreazione");
        jsonObject.putString("content", "contenuto");
        DearDiary.getApp().getStorage().create("memos", jsonObject);
        assertTrue(storage.getDirtyCreatedDocuments().size() == 1);
        List<JsonObject> dirtyObjects = storage.getDirtyCreatedDocuments();
        JsonObject jsonCreated = dirtyObjects.get(dirtyObjects.size() - 1);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 3);

        Wait.sleep2seconds();

        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)*/
        //AbstractPolicy.setLastSyncDate(new Date());
        storage.saveSyncDate(LocalStorage.generateNewDateAsString());

        Wait.sleep2seconds();

        /* eseguo il sync */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 0);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getLong("@version") != null);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getString("id") != null);

        String idServer = storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getString("id");
        String collection = storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getString("@class");

        BaasBox cli = BaasBox.getDefault();
        BaasResult<JsonObject> response = cli.restSync(HttpRequest.GET, "document/"+collection+"/"+idServer,  null, true);
        assertTrue(response.isSuccess());
        try {
            assertTrue(response.get().getObject("data").getString("id").equals(idServer));
        } catch (BaasException e) {
            e.printStackTrace();
        }
    }
}

