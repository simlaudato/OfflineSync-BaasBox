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

import java.io.IOException;
import java.util.List;

import offlinesync.LocalStorage;
import offlinesync.LocalSyncInfo;

/**
 * Created by Simone on 28/07/2014.
 */
public class GetTest  extends AndroidTestCase {
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


    public void test() throws IOException {
        //tests getByLocalID, getByID, getDirtyCreatedDocuments
        storage.deleteAllDocuments();
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.putString("title", "title");
        jsonObject1.putString("content", "content");
        storage.create("test", jsonObject1);
        String localID1 = storage.getDirtyCreatedDocuments().get(0).getObject("local_data").getString("localID");
        JsonObject jsonObject_1 = storage.getByLocalID(localID1, "test");
        assertTrue(jsonObject_1!=null);
        assertNull(jsonObject_1.getObject("data"));
        assertNull(jsonObject_1.getObject("local_data"));
        assertTrue(jsonObject_1.getString("title").equals("title"));
        assertTrue(jsonObject_1.getString("content").equals("content"));
        JsonObject jsonObject2 = storage.getByID(localID1, "test");
        assertEquals(jsonObject_1, jsonObject2);

        // tests getByRemoteID, _getByID, _getByIdWithoutCollection, updateAfterCreation
        BaasBox baasBox = BaasBox.getDefault();
        BaasResult<JsonObject> postResponse = baasBox.restSync(HttpRequest.POST, "document/test", jsonObject1, true);
        assertTrue(postResponse.isSuccess());
        try {
            storage.updateAfterCreation(storage._getByIDWithoutCollection(localID1), postResponse.get().getObject("data"));
        } catch (BaasException e) {
            e.printStackTrace();
        }

        JsonObject jsonUpdatedAfterCreation = storage._getByID(localID1, "test");
        JsonObject jsonUpdatedAfterCreation_1 = storage._getByIDWithoutCollection(localID1);
        assertEquals(jsonUpdatedAfterCreation, jsonUpdatedAfterCreation_1);
        String idServer = jsonUpdatedAfterCreation.getObject("data").getString("id");

        JsonObject jsonGetByRemoteID = storage.getByRemoteID(idServer, "test");
        assertNotNull(jsonGetByRemoteID);
        assertNull(jsonGetByRemoteID.getObject("data"));
        assertNull(jsonGetByRemoteID.getObject("local_data"));



        //tests listDocuments
        List<JsonObject> documentsList = storage.listDocuments();
        assertNotNull(documentsList);
        assertTrue(documentsList.size()!= 0);
        assertTrue(documentsList.size()==1);


        //tests getLocalSyncInfo
        LocalSyncInfo syncInfo = storage.getLocalSyncInfo(localID1);
        assertTrue(localID1.equals(syncInfo.getLocalID()));
        int dirty = syncInfo.getDirty();
        String update_date = syncInfo.getUpdate_date();
        assertEquals(jsonUpdatedAfterCreation.getObject("local_data").getLong("_dirty"), Long.valueOf(dirty));
        assertEquals(jsonUpdatedAfterCreation.getObject("local_data").getString("_update_date"), update_date);
    }

}