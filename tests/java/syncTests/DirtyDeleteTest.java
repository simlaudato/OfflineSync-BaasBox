package syncTests;

import android.content.SyncContext;
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
public class DirtyDeleteTest extends AndroidTestCase {

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
     * Tests the Synchronization with the Server after a local deletion of an object (also present on the Server)
     * This conflict will be won by the client, so the document will be deleted also on the Server
     */
    public void test() throws IOException, SyncException {
        storage.deleteAllDocuments();
        /* creo un record sul server e sul client (su cui poi fare una delete locale) */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "testDeletion");
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

        /* in questo test devo far attendere qualche secondo prima di eseguire setLastSyncDate(new Date())
        * altrimenti tale data risulterebbe > dell' _update_date del documento creato precedentemente poichè
        * la POST per crearlo impiega del tempo per essere eseguita mentre il test continua
        */
        Wait.sleep2seconds();

        /*devo settare una data di sync fittizia (poichè prima di questo test non c'è mai stata una sync)
        * altrimenti executeSyncAll(...) non esegue il primo passo poichè la last_sync_date sarebbe null */
        storage.saveSyncDate(LocalStorage.generateNewDateAsString());


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


        /* lo elimino sul client (dirty = 2) */
        storage.delete(idServer, "memos");
        assertTrue(storage._getByIDWithoutCollection(idServer).getObject("local_data").getLong("_dirty") == 2);

        /* faccio il sync (vince il client) */
        DefaultPolicy defaultPolicy = new DefaultPolicy();
        defaultPolicy.executeSyncAll(storage);
        assertTrue(storage._getByIDWithoutCollection(idServer) == null);
        //assert che il record sia finito nella struttura dati dedicata ( e non cancellato premanentemente )
        BaasResult<JsonObject> getResponse = baasBox.restSync(HttpRequest.GET, "deleted/document?where=id='" + idServer + "'", null, true);
        assertTrue(getResponse.isSuccess());
        Log.d("debug", "serverID: "+idServer);
        try {
            assertTrue(getResponse.get().getArray("data").size() == 1);
            assertTrue(getResponse.get().getArray("data").getObject(0).getString("_delete_date") != null);
        } catch (BaasException e) {
            e.printStackTrace();
        }

    }
}
