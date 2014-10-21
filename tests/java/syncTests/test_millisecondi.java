package syncTests;

import android.test.AndroidTestCase;
import android.util.Log;

import com.baasbox.android.BaasBox;
import com.baasbox.android.BaasException;
import com.baasbox.android.BaasHandler;
import com.baasbox.android.BaasResult;
import com.baasbox.android.BaasUser;
import com.baasbox.android.json.JsonArray;
import com.baasbox.android.json.JsonObject;
import com.baasbox.android.net.HttpRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import exceptions.SyncException;
import offlinesync.LocalStorage;

/**
 * Created by Simone on 09/09/2014.
 */
public class test_millisecondi extends AndroidTestCase {

    public void setUp() {
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
     * Tests the Synchronization with the Server after a local update of an object also updated on the Server.
     * This conflict (AS vs AC) will be won by the Server, so the local record will be replaced with that from server
     */

    public void test() throws UnsupportedEncodingException {

        //test basato su dei record presenti sul server in questo momento
        BaasBox baasBox = BaasBox.getDefault();

        /* passo la stessa data  e chiedo i record UGUALI -> mi aspetto che venga restituito un record */
        String listOfIdAndDateEncoded_0 = URLEncoder.encode("_update_date = date('2014-09-11T15:33:21.134+0200')", "utf-8");
        BaasResult<JsonObject> GET_result_0 = baasBox.restSync(HttpRequest.GET, "document/testMilllisecondi?where=" + listOfIdAndDateEncoded_0, null, true);
        assertTrue(GET_result_0.isSuccess());
        try {
            assertTrue(GET_result_0.get().getArray("data").size()==1);
        } catch (BaasException e) {
            e.printStackTrace();
        }


        /* passo la stessa data  e chiedo i record MAGGIORI-> mi aspetto che non venga restituito niente */
        String listOfIdAndDateEncoded_1 = URLEncoder.encode("_update_date > date('2014-09-11T15:33:21.134+0200')", "utf-8");
        BaasResult<JsonObject> GET_result_1 = baasBox.restSync(HttpRequest.GET, "document/testMilllisecondi?where=" + listOfIdAndDateEncoded_1, null, true);
        assertTrue(GET_result_1.isSuccess());
        try {
            assertTrue(GET_result_1.get().getArray("data").size()==0);
        } catch (BaasException e) {
            e.printStackTrace();
        }

        /* setto un millisecondo PRIMA e chiedo i record MAGGIORI-> mi aspetto che venga restituito un record*/
        String listOfIdAndDateEncoded_2 = URLEncoder.encode("_update_date > date('2014-09-11T15:33:21.133+0200')", "utf-8");
        BaasResult<JsonObject> GET_result_2 = baasBox.restSync(HttpRequest.GET, "document/testMilllisecondi?where=" + listOfIdAndDateEncoded_2, null, true);
        assertTrue(GET_result_2.isSuccess());
        try {
            assertTrue(GET_result_2.get().getArray("data").size()==1);
        } catch (BaasException e) {
            e.printStackTrace();
        }

         /* setto un millisecondo dopo e chiedo i record MINORI-> mi aspetto che vengano restituiti due record*/
        String listOfIdAndDateEncoded_3 = URLEncoder.encode("_update_date < date('2014-09-11T15:33:21.135+0200')", "utf-8");
        BaasResult<JsonObject> GET_result_3 = baasBox.restSync(HttpRequest.GET, "document/testMilllisecondi?where=" + listOfIdAndDateEncoded_3, null, true);
        assertTrue(GET_result_3.isSuccess());
        try {
            assertTrue(GET_result_3.get().getArray("data").size()==2);
        } catch (BaasException e) {
            e.printStackTrace();
        }

    }
}
