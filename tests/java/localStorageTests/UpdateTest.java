package localStorageTests;

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

import java.io.File;
import java.io.IOException;
import java.util.List;

import offlinesync.LocalStorage;

/**
 * Created by Simone on 04/09/2014.
 */
public class UpdateTest extends AndroidTestCase {

    LocalStorage storage;

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

    public void test() throws IOException {
        //tests update
        storage.deleteAllDocuments();
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "titolo");
        jsonObject.putString("content", "contenuto");
        storage.create("memos", jsonObject);
        assertTrue(storage.getRootDirectory().listFiles().length == 1);
        List<JsonObject> documentsList = storage.getDirtyCreatedDocuments();
        assertNotNull(documentsList);
        assertTrue(documentsList.size() == 1);
        String localID = documentsList.get(0).getObject("local_data").getString("localID");
        assertTrue(localID != null);

        storage.update(localID, "memos", "title", "titoloModificato");
        JsonObject jsonUpdated = storage._getByID(localID, "memos");
        assertTrue(jsonUpdated.getObject("data").getString("title").equals("titoloModificato"));
        // se era dirty created, dopo un aggiornamento deve rimanere dirty created
        assertEquals(jsonUpdated.getObject("local_data").getLong("_dirty"), Long.valueOf(3L));

        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/memos", jsonObject, true);
        assertTrue(postResponse.isSuccess());
        try {
            storage.updateAfterCreation(storage._getByIDWithoutCollection(localID), postResponse.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        storage.update(localID, "memos", "title", "titleUpdated");
        JsonObject jsonUpdated_1 = storage._getByID(localID, "memos");
        assertTrue(jsonUpdated_1.getObject("data").getString("title").equals("titleUpdated"));
        assertEquals(jsonUpdated_1.getObject("local_data").getLong("_dirty"), Long.valueOf(1L));
    }
}
