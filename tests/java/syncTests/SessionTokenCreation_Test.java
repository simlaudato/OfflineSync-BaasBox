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
import java.util.List;

import exceptions.SyncException;
import offlinesync.DefaultPolicy;
import offlinesync.LocalStorage;

/**
 * Created by Simone on 12/09/2014.
 */
public class SessionTokenCreation_Test extends AndroidTestCase{
        private LocalStorage storage;

        public void setUp() {
            storage = new LocalStorage(getContext(), true);
            BaasBox.Builder b = new BaasBox.Builder(getContext());
            BaasBox client = b.setApiDomain("192.168.56.1")
                    .setAppCode("1234567890").setPort(9000)
                    .init();
        }


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
            JsonObject jsonObject = new JsonObject();
            jsonObject.putString("title", "testCreazione");
            jsonObject.putString("content", "contenuto");
            DearDiary.getApp().getStorage().create("memos", jsonObject);
            assertTrue(storage.getDirtyCreatedDocuments().size() == 1);
            List<JsonObject> dirtyObjects = storage.getDirtyCreatedDocuments();
            JsonObject jsonCreated = dirtyObjects.get(dirtyObjects.size() - 1);
            assertTrue(storage._getByIDWithoutCollection(jsonCreated.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 3);


        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)
        * altrimenti executeSyncAll(...) non esegue il primo passo poichè la last_sync_date sarebbe null */
            storage.saveSyncDate(LocalStorage.generateNewDateAsString());

            Wait.sleep2seconds();


        /* faccio la prima sync (vince il client) */
            DefaultPolicy defaultPolicy = new DefaultPolicy();
            defaultPolicy.executeSyncAll(storage);

            Wait.sleep2seconds();

            /*altra creazione */
            JsonObject jsonObject_1 = new JsonObject();
            jsonObject_1.putString("title", "testCreazionee");
            jsonObject_1.putString("content", "contenuto");
            DearDiary.getApp().getStorage().create("memos", jsonObject_1);
            assertTrue(storage.getDirtyCreatedDocuments().size() == 1);
            List<JsonObject> dirtyObjects_1 = storage.getDirtyCreatedDocuments();
            JsonObject jsonCreated_1 = dirtyObjects_1.get(dirtyObjects_1.size() - 1);
            assertTrue(storage._getByIDWithoutCollection(jsonCreated_1.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 3);

            Wait.sleep2seconds();
        /* faccio la seconda sync (mi aspetto che non ottenga niente dal server */
            defaultPolicy.executeSyncAll(storage);
            //osservo dall'output che non vi sono documenti restituiti dal server

            Wait.sleep2seconds();


            /*altra creazione */
            JsonObject jsonObject_2 = new JsonObject();
            jsonObject_2.putString("title", "testCreazionee");
            jsonObject_2.putString("content", "contenuto");
            DearDiary.getApp().getStorage().create("memos", jsonObject_2);
            assertTrue(storage.getDirtyCreatedDocuments().size() == 1);
            List<JsonObject> dirtyObjects_2 = storage.getDirtyCreatedDocuments();
            JsonObject jsonCreated_2 = dirtyObjects_2.get(dirtyObjects_2.size() - 1);
            assertTrue(storage._getByIDWithoutCollection(jsonCreated_2.getObject("local_data").getString("localID")).getObject("local_data").getLong("_dirty") == 3);


            Wait.sleep2seconds();
        /* faccio la terza sync (mi aspetto che non ottenga niente dal server */
            defaultPolicy.executeSyncAll(storage);
        }
    }


