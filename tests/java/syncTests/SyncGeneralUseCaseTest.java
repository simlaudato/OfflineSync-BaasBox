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

/**
 * Tests the Synchronization with the Server after a previous sync in order to test the second sync
 *
 */
public class SyncGeneralUseCaseTest extends AndroidTestCase {

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
        Log.d("debug", "SESSION TOKEN SIMONE: "+BaasUser.current().getToken());

        Wait.sleep2seconds();

        storage.deleteAllDocuments();
        /* creo un record sul server e sul client */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testPrimaCreazione");
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

        /* lo modifico sul server */
        JsonObject jsonUpdate = new JsonObject();
        jsonUpdate.putString("title", "titoloTestPrimaCreazione");
        jsonUpdate.putString("content", "contenuto");
        BaasResult<JsonObject> putResponse = baasBox.restSync(HttpRequest.PUT, "document/memos/"+idServer,
                jsonUpdate, true);
        assertTrue(putResponse.isSuccess());
        try {
            assertTrue(putResponse.get().getObject("data").getString("title").equals("titoloTestPrimaCreazione"));
        } catch (BaasException e) {
            e.printStackTrace();
        }
        /* lo modifico sul client */
        storage.update(idServer, "memos", "title", "titoloAggiornato");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloAggiornato"));

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

        /* faccio la prima sync (vince il server) */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_last_sync_date")!=null);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty")==0);
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloTestPrimaCreazione"));
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("content").equals("contenuto"));

        Wait.sleep2seconds();

        Log.d("debug", "FINE PRIMA SYNCCCCCCCCCCCCCCCCCCCC");

        /* preparo le altre modifiche e faccio la seconda sync */
        /* una creazione */
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.putString("title", "testSecondaCreazione");
        jsonObject1.putString("content", "contenuto");
        DearDiary.getApp().getStorage().create("memos", jsonObject1);
        List<JsonObject> dirtyObjects1 = storage.getDirtyCreatedDocuments();
        assertTrue(dirtyObjects1.size() == 1);
        JsonObject jsonCreated1 = dirtyObjects1.get(dirtyObjects1.size() - 1);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 3);
        /* una modifica in locale ad un oggetto sincronizzato dopo la prima sync */
        storage.update(idServer, "memos", "title", "titoloUpdateAggiornatoSecondaSync");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("data").getString("title").equals("titoloUpdateAggiornatoSecondaSync"));
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty")==1);
        assertFalse(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_last_sync_date").
                equals(storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_update_date")));
        Log.d("debug", "updateDate: "+storage._getByIDWithoutCollection(idServer).getObject("local_data").getString("_update_date"));

        Wait.sleep2seconds();

        /*eseguo la seconda sync:
        * mi aspetto che venga effettuata la creazione e l'update anche sul server*/
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 0);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("data").getLong("@version") != null);
        assertTrue(storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("data").getString("id") != null);

        String idServerCreation = storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("data").getString("id");
        String collection = storage._getByIDWithoutCollection(jsonCreated1.getObject("local_data").getString("localID")).getObject("data").getString("@class");

        BaasBox cli = BaasBox.getDefault();
        BaasResult<JsonObject> response = cli.restSync(HttpRequest.GET, "document/"+collection+"/"+idServer,  null, true);
        assertTrue(response.isSuccess());

        try {
            assertTrue(response.get().getObject("data").getString("id").equals(idServer));
            Log.d("debug", "CONTROLLA QUI L'OGGETTO DOPO LA GET "+ response.get().getObject("data"));
            assertTrue(response.get().getObject("data").getString("title").equals("titoloUpdateAggiornatoSecondaSync"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        Wait.sleep2seconds();

        /* preparo le altre modifiche e faccio la terza sync */
        /* una cancellazione sul client del record creato precedentemente */
        storage.delete(idServerCreation, "memos");
        assertTrue(storage._getByIDWithoutCollection(idServerCreation).getObject("local_data").getLong("_dirty") == 2);

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

        BaasUser user_2 = BaasUser.withUserName("simo")
                .setPassword("simo");
        user_2.login(new BaasHandler<BaasUser>() {
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

        /* un update sul server del record creato precedentemente */
        JsonObject jsonUpdated = new JsonObject();
        jsonUpdated.putString("title", "titoloCancellazione");
        jsonUpdated.putString("content", "contenuto");
        BaasResult<JsonObject> putResponse1 = baasBox.restSync(HttpRequest.PUT, "document/memos/"+idServerCreation,
                jsonUpdated, true);
        assertTrue(putResponse1.isSuccess());
        try {
            assertTrue(putResponse1.get().getObject("data").getString("title").equals("titoloCancellazione"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        Wait.sleep2seconds();

        assertTrue(BaasUser.current()!=null);
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

        BaasUser user_3 = BaasUser.withUserName("andrea")
                .setPassword("andrea");
        user_3.login(new BaasHandler<BaasUser>() {
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

        /* eseguo la terza sync (il conflitto viene vinto dal client ->cancellazione sul server) */
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServerCreation) == null);
        //assert che il record sia finito nella struttura dati dedicata
        BaasResult<JsonObject> getResponse = baasBox.restSync(HttpRequest.GET, "deleted/document?where=id='" + idServerCreation + "'", null, true);
        assertTrue(getResponse.isSuccess());
        try {
            assertTrue(getResponse.get().getArray("data").size() == 1);
            assertTrue(getResponse.get().getArray("data").getObject(0).getString("_delete_date") != null);
        } catch (BaasException e) {
            e.printStackTrace();
        }

    }

}