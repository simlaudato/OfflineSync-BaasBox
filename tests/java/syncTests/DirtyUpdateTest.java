package syncTests;

/**
 * Created by Simone on 31/07/2014.
 */

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

public class DirtyUpdateTest extends AndroidTestCase {

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
     * Tests the Synchronization with the Server after a local update of an object (also present on the Server)
     * There will be no conflict due to the _update_date (on Server) will be = _last_sync_date (on Client), so
     * no objects will be given from the first GET_API on the Server.
     * Only the second part of the SyncAlgorithm will be executed
     */
    public void test() throws IOException, SyncException {
        storage.deleteAllDocuments();
        /* creo un record sul server e sul client */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testUpdate");
        jsonObject.putString("content", "contenuto");
        DearDiary.getApp().getStorage().create("test", jsonObject);
        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/test", jsonObject, true);
        assertTrue(postResponse.isSuccess());
        List<JsonObject> dirtyObjects = storage.getDirtyCreatedDocuments();
        assertTrue(dirtyObjects.size() == 1);
        JsonObject jsonCreated = dirtyObjects.get(dirtyObjects.size() - 1);
        try {
            storage.updateAfterCreation(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")), postResponse.get().getObject("data"));
            Log.d("debug", "creationUpdateDate"+postResponse.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        Wait.sleep2seconds();

        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)
            * altrimenti executeSyncAll(...) non esegue il primo passo poichè la last_sync_date sarebbe null */
        String date = LocalStorage.generateNewDateAsString();
        storage.saveSyncDate(date);
        Log.d("debug", "set sync Date: "+date);

        //String syncDateAfterUpdateAfterCreation = storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("local_data").getString("_last_sync_date");
        String idServer = storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("data").getString("id");


        /* devo fare il login con un altro utente (che effettuerà l'update sul proprio client)
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


        /* lo modifico sul client */
        storage.update(idServer, "test", "title", "titoloUpdateAggiornato");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloUpdateAggiornato"));
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty")==1);
        assertFalse(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_last_sync_date").
                equals(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_update_date")));
        Log.d("debug", "updateDate: "+storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_update_date"));

        Wait.sleep2seconds();

        /* faccio il sync (non c'è il conflitto, viene mandato l'update sul server) */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty")==0L);
        BaasResult<JsonObject> getResponse = baasBox.restSync(HttpRequest.GET, "document/test/"+idServer,
                null, true);
        assertTrue(getResponse.isSuccess());
        try {
            assertTrue(getResponse.get().getObject("data").getString("title").equals("titoloUpdateAggiornato"));
            Log.d("debug", "updateOnServer"+getResponse.get().getObject("data").getString("_update_date"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        Log.d("debug", "syncDate: "+storage.getSyncDate());
    }
}

