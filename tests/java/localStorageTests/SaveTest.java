package localStorageTests;

import android.test.AndroidTestCase;
import android.util.Log;

import com.baasbox.android.BaasBox;
import com.baasbox.android.BaasException;
import com.baasbox.android.BaasResult;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.net.HttpRequest;

import java.io.File;

import offlinesync.LocalStorage;

/**
 * Created by Simone on 03/09/2014.
 */
public class SaveTest extends AndroidTestCase{
    private LocalStorage storage;


    public void setUp() {
        storage = new LocalStorage(getContext(),true);
        BaasBox.Builder b = new BaasBox.Builder(getContext());
        BaasBox client = b.setApiDomain("192.168.56.1")
                .setAppCode("1234567890").setPort(9000)
                .init();
    }

    public void test(){
        //tests saveJson
        storage.deleteAllDocuments();
        //costruisco un json object fittizio "a mano"
        JsonObject jsonObject = new JsonObject();
        JsonObject localData = new JsonObject();
        JsonObject data = new JsonObject();
        localData.putString("localID", "Lxyz");
        data.putString("id", "uvxyz");
        jsonObject.putObject("local_data", localData);
        jsonObject.putObject("data", data);
        storage.saveJson(jsonObject);
        File[] files = storage.getRootDirectory().listFiles();
        assertTrue(files.length!=0);
        File file = files[0];
        assertTrue(("uvxyz_Lxyz").equals(file.getName()));



        //tests saveDocument
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.putString("title", "title");
        jsonObject1.putString("content", "content");
        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/test", jsonObject1, true);
        assertTrue(postResponse.isSuccess());

        String serverID = null;
        try {
            serverID = postResponse.get().getObject("data").getString("id");
            Log.d("debug", "SERVER ID"+serverID);
            BaasResult<JsonObject> GET_result = baasBox.restSync(HttpRequest.GET, "document/test/"+serverID, null, true);
            assertTrue(GET_result.isSuccess());
            try {
                storage.saveDocument(GET_result.get().getObject("data"), GET_result.get().getString("server_datetime"));
                JsonObject jsonObject_1 = storage._getByID(serverID, "test");
                assertNotNull(jsonObject_1);
                assertNotNull(jsonObject_1.getObject("local_data"));
                assertNotNull(jsonObject_1.getObject("data"));
                assertTrue(jsonObject_1.getObject("data").getString("title").equals("title"));
                assertTrue(jsonObject_1.getObject("data").getString("content").equals("content"));
                assertNotNull(jsonObject_1.getObject("data").getString("id"));
                assertNotNull(jsonObject_1.getObject("data").getString("@class"));
                assertTrue(jsonObject_1.getObject("data").getString("@class").equals("test"));
                assertNotNull(jsonObject_1.getObject("data").getLong("@version"));
                assertNotNull(jsonObject_1.getObject("data").getString("_author"));
                assertEquals(jsonObject_1.getObject("data").getString("id"), jsonObject_1.getObject("local_data").getString("localID"));
                assertNotNull(jsonObject_1.getObject("local_data").getString("_update_date"));
                assertNotNull(jsonObject_1.getObject("local_data").getString("_last_sync_date"));
                assertEquals(jsonObject_1.getObject("local_data").getLong("_dirty"), Long.valueOf(0L));
            } catch (BaasException e) {
                e.printStackTrace();
            }

        } catch (BaasException e) {
            e.printStackTrace();
        }


    }
}
