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

import java.util.Date;
import java.util.List;

import offlinesync.LocalStorage;

/**
 * Created by Simone on 29/07/2014.
 */
public class DeleteTest extends AndroidTestCase {
    private LocalStorage storage;

    public void setUp(){
        storage = new LocalStorage(getContext(),true);
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

    public void test(){

        //tests delete of dirty created document
        storage.deleteAllDocuments();
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.putString("title", "title");
        jsonObject1.putString("content", "content");
        storage.create("memos", jsonObject1);
        assertEquals(1, storage.getRootDirectory().listFiles().length);
        String localID1 = storage.getDirtyCreatedDocuments().get(0).getObject("local_data").getString("localID");
        storage.delete(localID1, "memos");
        assertEquals(0, storage.getRootDirectory().listFiles().length);
        //assertEquals(Long.valueOf(2), storage._getByID(localID1, "memos").getLong("_dirty"));

        //tests delete of not dirty document
        JsonObject jsonObject_1 = new JsonObject();
        jsonObject1.putString("title", "title");
        jsonObject1.putString("content", "content");
        storage.create("memos", jsonObject_1);
        String localID_1 = storage.getDirtyCreatedDocuments().get(0).getObject("local_data").getString("localID");
        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/memos", jsonObject_1, true);
        assertTrue(postResponse.isSuccess());
        try {
            storage.updateAfterCreation(storage._getByIDWithoutCollection(localID_1), postResponse.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }
        storage.delete(localID_1, "memos");
        assertEquals(storage._getByID(localID_1, "memos").getObject("local_data").getLong("_dirty"), Long.valueOf(2L));


        //tests deleteFieldTest
        storage.deleteAllDocuments();
        JsonObject jsonObject2 = new JsonObject();
        jsonObject2.putString("title", "title2");
        jsonObject2.putString("content", "content2");
        storage.create("memos", jsonObject2);
        assertEquals(1, storage.getRootDirectory().listFiles().length);
        String localID2 = storage.getDirtyCreatedDocuments().get(0).getObject("local_data").getString("localID");
        DearDiary.getApp().getStorage().deleteField(localID2, "memos", "content");
        assertNull(DearDiary.getApp().getStorage()._getByID(localID2, "memos").getObject("data").getString("content"));

        //deleteDocumentsByDateTest
       /* JsonObject jsonObject3 = new JsonObject();
        jsonObject1.putString("title", "title3");
        jsonObject1.putString("content", "content3");
        storage.create("memos", jsonObject3);
        assertEquals(1, storage.getRootDirectory().listFiles().length);
        String localID3 = storage.getDirtyCreatedDocuments().get(0).getObject("local_data").getString("localID");
        storage.delete(localID3, "memos");
        assertEquals(0, storage.getRootDirectory().listFiles().length);
        assertEquals(Long.valueOf(2), storage._getByID(localID3, "memos").getLong("_dirty"));
        List<JsonObject> deletedDocuments = storage.deleteDocumentsByDate(new Date());
        assertNotNull(deletedDocuments);
        assertEquals(1, deletedDocuments.size());
        assertTrue(deletedDocuments.get(0).getObject("local_data").getString("localID").equals(localID3));*/
    }

}
