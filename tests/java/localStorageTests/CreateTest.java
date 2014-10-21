package localStorageTests;

import android.test.AndroidTestCase;
import android.util.Log;

import com.baasbox.android.BaasBox;
import com.baasbox.android.BaasHandler;
import com.baasbox.android.BaasResult;
import com.baasbox.android.BaasUser;
import com.baasbox.android.json.JsonObject;
import com.baasbox.deardiary.DearDiary;

import java.io.File;
import java.io.IOException;
import java.util.List;

import offlinesync.LocalStorage;

/**
 * Created by Simone on 28/07/2014.
 */
public class CreateTest extends AndroidTestCase {

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
        storage.deleteAllDocuments();
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("title", "titolo");
        jsonObject.putString("content", "contenuto");
        storage.create("test", jsonObject);
        assertTrue(storage != null);
        assertTrue(storage.getRootDirectory().listFiles().length == 1);
        List<JsonObject> documentsList = storage.getDirtyCreatedDocuments();
        assertNotNull(documentsList);
        assertTrue(documentsList.size() == 1);
        String localID = documentsList.get(0).getObject("local_data").getString("localID");
        assertTrue(localID != null);
        File[] files = storage.getRootDirectory().listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        assertNotNull(JsonObject.decode(LocalStorage.convertFile(files[0])).getObject("local_data").getString("localID"));
    }


}