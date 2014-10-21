package offlinesync;

/**
 * Created by Simone on 16/07/2014.
 */

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import com.baasbox.android.json.*;

import org.apache.commons.lang3.exception.ExceptionUtils;

import exceptions.CollectionIdException;
import exceptions.CreationFieldException;
import exceptions.DeleteFileException;
import exceptions.DirectoryException;
import exceptions.DirectoryNotCreatedException;
import exceptions.InvalidFieldValueUpdateException;
import exceptions.LocalIDException;
import exceptions.MultipleIdException;
import exceptions.SaveException;
import exceptions.UpdateFieldException;
import exceptions.WrongIdSearchException;

/**
 * Local Storage: Directory containing BaasBox Documents saved in File format and mapped as JsonObjects
 * Name of the document = RUUID_LUUID
 * document = {
 *              "local_data":{
 *                  "localID":"...",
 *                  "_dirty":"...",
 *                  "_last_sync_date":"...",
 *                  "_update_date":"..." },
 *              "data":{
 *                  "id":"..."
 *                  "@class":"..."
 *                  "_author":"..."
 *                  "@version":"..."}
 *             }
 **/

public class LocalStorage {

    private static final String dataField = "data";
    private static final String localDataField = "local_data";
    private static final String localID = "localID";
    private File rootDirectory;


    // -------------------------- CONSTRUCTOR --------------------------


    public LocalStorage(Context context, boolean external) {
        File directory = external ? getExternalDir() : getInternalDir(context);
        directory.mkdirs();
        Log.d("LOCAL", "Dir name: " + directory.getAbsolutePath());
        rootDirectory = directory;
        /* set an artificial lastSyncDate */
        String lastSyncDate = "1900-01-01T00:00:00.000+0200";
        saveSyncDate(lastSyncDate);
    }

    private LocalStorage(File file) {
        rootDirectory = file;
    }


    private static File getExternalDir() {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        return new File(externalStorageDirectory, "baasbox-storage-dir");
    }

    private static File getInternalDir(Context context) {
        File filesDir = context.getFilesDir();
        return new File(filesDir, "baasbox-storage-dir");
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    // -------------------------- STORAGE METHODS --------------------------

    // -------------------------- GET --------------------------

    /**
     * Retrieves the JsonObject related to a document saved in the Local Storage in File format
     *
     * @param id ("localID"), collection
     * @return JsonObject related to the document if it exists, null otherwise
     */
    public synchronized JsonObject getByLocalID(final String id, String collection) {
        if (id == null || collection == null || collection.length() == 0) {
            try {
                throw new CollectionIdException();
            } catch (CollectionIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        }
        if (!id.startsWith("L"))
            try {
                throw new LocalIDException();
            } catch (LocalIDException e) {
                e.printStackTrace();
                return null;
            }

        File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String s) {
                return s.endsWith("_" + id);
            }
        });
        if (files.length == 0) return null; //oggetto non trovato
        if (files.length > 1) try {
            throw new MultipleIdException();
        } catch (MultipleIdException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }

        JsonObject jsonObject = null;
        try {
            jsonObject = JsonObject.decode(convertFile(files[0]));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        if (jsonObject != null && jsonObject.getObject(localDataField).getString(localID) != null
                && jsonObject.getObject(dataField).getString("@class") != null
                && jsonObject.getObject(localDataField).getString(localID).equals(id)
                && jsonObject.getObject(dataField).getString("@class").equals(collection))
            return jsonObject.getObject(dataField);
        else try {
            throw new CollectionIdException();
        } catch (CollectionIdException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Retrieves the JsonObject related to a document saved in the Local Storage in File format
     *
     * @param id (remote), collection
     * @return JsonObject related to the document if it exists, null otherwise
     */
    public synchronized JsonObject getByRemoteID(final String id, String collection) {
        if (id == null || collection == null || collection.length() == 0) {
            try {
                throw new CollectionIdException();
            } catch (CollectionIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        }
        if (id.startsWith("L")) try {
            throw new WrongIdSearchException();
        } catch (WrongIdSearchException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }

        File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String s) {
                return s.startsWith(id + "_");
            }
        });
        if (files.length == 0) return null; //oggetto non trovato
        if (files.length > 1) try {
            throw new MultipleIdException();
        } catch (MultipleIdException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }

        JsonObject jsonObject = null;
        try {
            jsonObject = JsonObject.decode(convertFile(files[0]));
        } catch (IOException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
        if (jsonObject != null && jsonObject.getObject(dataField).getString("id") != null
                && jsonObject.getObject(dataField).getString("@class") != null
                && jsonObject.getObject(dataField).getString("id").equals(id)
                && jsonObject.getObject(dataField).getString("@class").equals(collection))
            return jsonObject.getObject(dataField);
        else try {
            throw new CollectionIdException();
        } catch (CollectionIdException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Retrieves the JsonObject related to a document saved in the Local Storage in File format
     *
     * @param id whether remote or local, collection
     * @return JsonObject related to the document if it exists, null otherwise
     */
    public synchronized JsonObject getByID(String id, String collection) {
        if (id.startsWith("L"))
            return getByLocalID(id, collection);
        else
            return getByRemoteID(id, collection);
    }

    /**
     * Retrieves the list of the current JsonObjects in the Local Storage
     *
     * @return List<JsonObject>
     */
    public List<JsonObject> listDocuments() {
        List<JsonObject> listOfDocuments = new ArrayList<JsonObject>();
        if (this.rootDirectory == null)
            try {
                throw new DirectoryException();
            } catch (DirectoryException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        File[] file = this.rootDirectory.listFiles();
        if(file.length!=0) {
            for (File f : file) {
                if (!f.getName().equals("syncDate")) {
                    try {
                        JsonObject currentJson = JsonObject.decode(convertFile(f));
                        listOfDocuments.add(currentJson);
                    } catch (IOException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        return null;
                    }
                }
            }
        }
        return listOfDocuments;
    }

    /**
     * Retrieves the list of the current dirty JsonObjects in the Local Storage
     *
     * @return List<JsonObject>
     */
    public synchronized List<JsonObject> getDirtyDocuments() {
        List<JsonObject> dirtyJsonObjects = new ArrayList<JsonObject>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if (!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = null;
                        try {
                            jsonObject = JsonObject.decode(convertFile(f));
                        } catch (IOException e) {
                            Log.d("error", ExceptionUtils.getStackTrace(e));
                        }
                        if (jsonObject != null && jsonObject.getObject(localDataField).getLong("_dirty") != 0)
                            dirtyJsonObjects.add(jsonObject);
                    }
                }
            }
        }
        return dirtyJsonObjects;
    }

    /**
     * Retrieves the list of the current dirty "created" (3) JsonObjects in the Local Storage
     *
     * @return List<JsonObject>
     */
    public synchronized List<JsonObject> getDirtyCreatedDocuments() {
        List<JsonObject> dirtyJsonObjects = new ArrayList<JsonObject>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if (!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = null;
                        try {
                            jsonObject = JsonObject.decode(convertFile(f));
                        } catch (IOException e) {
                            Log.d("error", ExceptionUtils.getStackTrace(e));
                        }
                        if (jsonObject != null && jsonObject.getObject(localDataField).getLong("_dirty") == 3)
                            dirtyJsonObjects.add(jsonObject);
                    }
                }
            }
        }
        return dirtyJsonObjects;
    }

    /**
     * Retrieves the LocalSyncInfo related to a document in the Local Storage
     *
     * @param id of the document
     * @return LocalSyncInfo
     */
    public synchronized LocalSyncInfo getLocalSyncInfo(String id) {
        JsonObject jsonObject = _getByIDWithoutCollection(id);
        JsonObject localJsonObject = jsonObject.getObject(localDataField);
        JsonObject remoteJsonObject = jsonObject.getObject(dataField);
        return new LocalSyncInfo(localJsonObject.getString(localID), localJsonObject.getLong("_dirty").intValue(),
                localJsonObject.getString("_last_sync_date"), localJsonObject.getString("_update_date"), remoteJsonObject.getLong("@version"));

    }

    /**
     * Retrieves the RemoteSyncInfo related to a document in the Local Storage
     *
     * @param jsonDocument ("d related to a BaasDocument retrieved from Server
     * @return RemoteSyncInfo
     */
    public synchronized RemoteSyncInfo getRemoteSyncInfo(JsonObject jsonDocument) {
        return new RemoteSyncInfo(jsonDocument.getString("id"), jsonDocument.getString("_delete_date"),
                jsonDocument.getString("_update_date"), jsonDocument.getLong("@version"));
    }

    // -------------------------- CREATE --------------------------

    /**
     * Creates a document in the Local Storage saving it in File format
     *
     * @param collection and jsonToCreate (content of the document)
     * @return localID of the document, if it has been created
     */
    public String create(String collection, JsonObject jsonToCreate) {
        if (!JsonWithoutReservedFields(jsonToCreate))
            try {
                throw new CreationFieldException();
            } catch (CreationFieldException e) {
                e.printStackTrace();
                return null;
            }
        else {
            /* local data*/
            JsonObject localJson = new JsonObject();
            localJson.putString(localID, "L" + UUID.randomUUID().toString());
            localJson.putLong("_dirty", 3);
            localJson.putString("_update_date", generateNewDateAsString());
            localJson.putNull("_last_sync_date");
            /* remote data */
            jsonToCreate.putNull("id");
            jsonToCreate.putString("@class", collection);
            jsonToCreate.putNull("_author");
            /* complete Json to be saved */
            JsonObject completeJson = new JsonObject();
            completeJson.putObject(localDataField, localJson);
            completeJson.putObject(dataField, jsonToCreate);
            saveJson(completeJson);
            return localJson.getString(localID);
        }
    }


    // -------------------------- UPDATE --------------------------

    /**
     * Updates a document in the Local Storage depending on it had been created offline or not
     *
     * @param id, newJsonDocument, id ("id" or "offlineID"), typeOfJsonData ("local_data" or "remote_data")
     * @return JsonObject updated -- if the document has been updated with success, false otherwise
     */
    public synchronized JsonObject update(String id, String collection, String fieldName, Object value) {
        if (!checkFieldName(fieldName)) try {
            throw new UpdateFieldException();
        } catch (UpdateFieldException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
        JsonObject jsonObject = _getByID(id, collection);
        updateFieldByType(jsonObject.getObject(dataField), fieldName, value);
        /* If it is a dirty created object update, the document should remain dirty created (3)*/
        if (jsonObject.getObject(localDataField).getLong("_dirty") != 3)
            jsonObject.getObject(localDataField).putLong("_dirty", 1);
        String date = jsonObject.getObject(localDataField).getString("_update_date");
        if (date != null) {
            String newDate = getDateUsingTimeZone(date);
            jsonObject.getObject(localDataField).putString("_update_date", newDate);
        } else
            jsonObject.getObject(localDataField).putString("_update_date", generateNewDateAsString());
        String fileName = jsonObject.getObject(dataField).getString("id") + "_" + jsonObject.getObject(localDataField).getString(localID);
        if (jsonObject.getObject(dataField).getString("id") == null)
            fileName = "_" + jsonObject.getObject(localDataField).getString(localID);
        if (!saveJson(jsonObject).equals(fileName))
            try {
                throw new SaveException();
            } catch (SaveException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        return jsonObject.getObject(dataField);
    }

    /**
     * Update fields: "id", "last_sync_date", "_update_date" after a CREATE (on Server) of a document created offline
     *
     * @param dirtyObject in the Storage and responseObjectFromServer ("data" field of the json) retrieved from Server
     * @return name of the document
     */
    public synchronized String updateAfterCreation(JsonObject dirtyObject, JsonObject responseObjectFromServer) {
        Log.d("debug", "updateAfterCreation - start");
        Log.d("debug", "updateAfterCreation - dirtyObject: " + dirtyObject.toString());
        Log.d("debug", "updateAfterCreation - responseObjectFromServer: " + responseObjectFromServer.toString());
        responseObjectFromServer.without("@rid"); // tolgo il @rid (non serve offline)
        responseObjectFromServer.without("_update_date");
        responseObjectFromServer.without("_delete_date");
        responseObjectFromServer.without("_clazz");
        responseObjectFromServer.without("_version");

        dirtyObject.putObject(dataField, responseObjectFromServer);
        String date = responseObjectFromServer.getString("_creation_date");
        dirtyObject.getObject(localDataField).putString("_update_date", date);
        dirtyObject.getObject(localDataField).putString("_last_sync_date", date);
        dirtyObject.getObject(localDataField).putLong("_dirty", 0);
        dirtyObject.getObject(dataField).remove("_creation_date");


        File f = new File(this.rootDirectory + "/_" + dirtyObject.getObject(localDataField).getString(localID));
        if (f.exists()) {
            if (!f.delete()) {
                try {
                    throw new DeleteFileException();
                } catch (DeleteFileException e) {
                    Log.d("error", ExceptionUtils.getStackTrace(e));
                    return null;
                }
            }
            String json = dirtyObject.encode();
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(this.rootDirectory + "/" + dirtyObject.getObject(dataField).getString("id") + "_" + dirtyObject.getObject(localDataField).getString(localID))); // nomefile = RUUID_LUUID
                writer.write(json);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else throw new IllegalArgumentException("File not found on the Storage");
        return dirtyObject.getObject(dataField).getString("id") + "_" + dirtyObject.getObject(localDataField).getString(localID);
    }

    // -------------------------- DELETE --------------------------

    /**
     * Deletes a document in the Local Storage:
     * If the document had been created offline it will be deleted permanently,
     * otherwise its dirty field will be set to the value #2 and its _update_date field will be set to the current date
     *
     * @param id and collection related to the document to be deleted
     */
    public synchronized void delete(String id, String collection) {
        JsonObject jsonObject = _getByID(id, collection);
        if (jsonObject != null) {
            if (jsonObject.getObject(dataField).getString("id") == null)
                deleteOfflineDocument(jsonObject);
            else {
                jsonObject.getObject(localDataField).putLong("_dirty", 2);
                String date = jsonObject.getObject(localDataField).getString("_update_date");
                if (date != null) {
                    String newDate = getDateUsingTimeZone(date);
                    jsonObject.getObject(localDataField).putString("_update_date", newDate);
                } else
                    jsonObject.getObject(localDataField).putString("_update_date", generateNewDateAsString());
                if (!saveJson(jsonObject).equals(jsonObject.getObject(dataField).getString("id") + "_" + jsonObject.getObject(localDataField).getString(localID)))
                    try {
                        throw new SaveException();
                    } catch (SaveException e) {
                        e.printStackTrace();
                    }
            }
        } else throw new IllegalArgumentException("Document not found: wrong id or collection");
    }

    /**
     * Deletes a specific field of a document in the Local Storage
     *
     * @param id ("id" or "offlineID"), collection, fieldName
     * @return JsonObject updated, if the document has been updated with success
     */
    public synchronized JsonObject deleteField(String id, String collection, String fieldName) {
        if (!checkFieldName(fieldName)) try {
            throw new UpdateFieldException();
        } catch (UpdateFieldException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
        JsonObject jsonObject = _getByID(id, collection);
        jsonObject.getObject(dataField).remove(fieldName);
        /* If it is a dirty created object field update, the document should remain dirty created (3)*/        if (jsonObject.getObject(localDataField).getLong("_dirty") != 3)
            jsonObject.getObject(localDataField).putLong("_dirty", 1);
        String date = jsonObject.getObject(localDataField).getString("_update_date");
        if (date != null) {
            String newDate = getDateUsingTimeZone(date);
            jsonObject.getObject(localDataField).putString("_update_date", newDate);
        } else
            jsonObject.getObject(localDataField).putString("_update_date", generateNewDateAsString());

        String fileName = jsonObject.getObject(dataField).getString("id") + "_" + jsonObject.getObject(localDataField).getString(localID);
        if (jsonObject.getObject(dataField).getString("id") == null)
            fileName = "_" + jsonObject.getObject(localDataField).getString(localID);

        if (!saveJson(jsonObject).equals(fileName))
            try {
                throw new SaveException();
            } catch (SaveException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        return jsonObject.getObject(dataField);
    }

    /**
     * Deletes permanently a document created offline in the Local Storage
     *
     * @param fileName of the document to be deleted
     */
    public synchronized void deleteOfflineDocument(String fileName) {
        File[] files = this.rootDirectory.listFiles();
        if (files != null && files.length != 0) {
            for (File f : files) {
                if (f.getName().equals(fileName)) {
                    if (!f.delete()) {
                        try {
                            throw new DeleteFileException();
                        } catch (DeleteFileException e) {
                            Log.d("error", ExceptionUtils.getStackTrace(e));
                        }
                    }
                }
            }
        } else try {
            throw new DirectoryException();
        } catch (DirectoryException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Deletes permanently a document created offline (dirty created) in the Local Storage
     *
     * @param toDelete: JsonObject related to the document to be deleted
     */
    public boolean deleteOfflineDocument(JsonObject toDelete) {
        String fileName = "_" + toDelete.getObject("local_data").getString("localID");
        deleteOfflineDocument(fileName);
        return true;
    }

    /**
     * Deletes permanently a document (not dirty created) in the Local Storage
     *
     * @param jsonObject of the document to be deleted
     */
    public synchronized void deleteDocument(JsonObject jsonObject) {
        File[] files = this.rootDirectory.listFiles();
        if (files != null && files.length != 0) {
            for (File f : files) {
                if (f.getName().equals(jsonObject.getObject("data").getString("id") + "_" + jsonObject.getObject(localDataField).getString(localID))) {
                    if (!f.delete()) {
                        try {
                            throw new DeleteFileException();
                        } catch (DeleteFileException e) {
                            Log.d("error", ExceptionUtils.getStackTrace(e));
                        }
                    }
                }
            }
        } else try {
            throw new DirectoryException();
        } catch (DirectoryException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
        }
    }


    /**
     * Deletes all the documents in the Local Storage with the field _last_sync_date < date
     *
     * @param date to compare to _last_sync_date
     * @return List<SyncInfo> containing the documents deleted with success
     */
    public synchronized List<JsonObject> deleteDocumentsByDate(Date date) {
        List<JsonObject> deletedDocuments = new ArrayList<JsonObject>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    JsonObject jsonObject = null;
                    try {
                        jsonObject = JsonObject.decode(convertFile(f));
                    } catch (IOException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        return null;
                    }
                    String _last_sync_date = jsonObject.getObject(localDataField).getString("_last_sync_date");
                    if (_last_sync_date != null && date != null) {
                        Date _last_sync = generateDateFromString(_last_sync_date);
                        if (_last_sync.before(date)) {
                            deletedDocuments.add(jsonObject);
                            deleteOfflineDocument(f.getName());
                        }
                    } else
                        throw new IllegalArgumentException("_last_sync_date or date given is null");
                }
            } else try {
                throw new DirectoryException();
            } catch (DirectoryException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        } else try {
            throw new DirectoryException();
        } catch (DirectoryException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
        return deletedDocuments;
    }

    public synchronized void deleteAllDocuments() {
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if (!f.delete()) {
                        try {
                            throw new DeleteFileException();
                        } catch (DeleteFileException e) {
                            Log.d("error", ExceptionUtils.getStackTrace(e));
                        }
                    }
                }
            }
        }
    }



    // -------------------------- SAVE --------------------------

    /**
     * Saves a document in the Local Storage in File format
     *
     * @param jsonObject related to the Document to be saved
     * @return name of document if it has been saved with success
     */

    public synchronized String saveJson(JsonObject jsonObject) {
        File jsonFile = new File(this.rootDirectory, jsonObject.getObject(dataField).getString("id") + "_" + jsonObject.getObject(localDataField).getString(localID));
        if (jsonObject.getObject(dataField).getString("id") == null)
            jsonFile = new File(this.rootDirectory, "_" + jsonObject.getObject(localDataField).getString(localID));
        if (jsonFile.exists())
            if (!jsonFile.delete()){
                try {
                    throw new DeleteFileException();
                } catch (DeleteFileException e) {
                    Log.d("error", ExceptionUtils.getStackTrace(e));
                    return null;
                }
            }
        String json = jsonObject.encode();
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(jsonFile));
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
            }
        }
        return jsonFile.getName();
    }

    /**
     * Saves a BaasDocument (retrieved from Server by passthrough method) in the Local Storage in File format
     *
     * @param jsonFromDocument ("data") related to document to be saved
     * @return true if the document has been saved with success, false otherwise
     */
    public String saveDocument(JsonObject jsonFromDocument, String dateFromServer) {
        /* local data */
        JsonObject localObject = new JsonObject();
        localObject.putString(localID, jsonFromDocument.getString("id"));
        localObject.putLong("_dirty", 0);
        localObject.putString("_last_sync_date", dateFromServer);
        localObject.putString("_update_date", dateFromServer);
        /* remote data */
        JsonObject remoteObject = new JsonObject();
        remoteObject.putString("id", jsonFromDocument.getString("id"));
        remoteObject.putString("@class", jsonFromDocument.getString("@class"));
        remoteObject.putLong("@version", jsonFromDocument.getLong("@version"));
        remoteObject.putString("_author", jsonFromDocument.getString("_author"));
        JsonObject jsonData = putLegalFieldsOnly(jsonFromDocument);
        remoteObject.mergeMissing(jsonData);
        /* complete Json to be saved */
        JsonObject jsonObject = new JsonObject();
        jsonObject.putObject(localDataField, localObject);
        jsonObject.putObject(dataField, remoteObject);
        return saveJson(jsonObject);
    }


    // -------------------------- PRIVATE METHODS --------------------------

    /**
     * Saves the given date in the dedicated file ("syncDate") in the local storage
     * The given date will be saved as a String in BaasBox date format
     *
     * @param date to be saved
     */
    public void saveSyncDate(String date) {
        if (date != null) {
            File f = new File(this.rootDirectory, "syncDate");
            if (f.exists()) {
                if (!f.delete()) {
                    try {
                        throw new DeleteFileException();
                    } catch (DeleteFileException e) {
                        Log.d("error", ExceptionUtils.getStackTrace(e));
                        return;
                    }
                }
            }
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(f));
                writer.write(date);
                writer.flush();
            } catch (IOException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));

            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else{
            throw new IllegalArgumentException("The given date is not valid");
        }
    }
    /**
     * Retrieves the date in the dedicated file ("syncDate") in the local storage (if it exists)
     *
     * @return date in String format
     */
     public String getSyncDate(){
         File f = new File(this.rootDirectory, "syncDate");
         String line = null;
         if(!f.exists()){
             return null;
         }
         else {
             BufferedReader reader = null;
             try {
                 reader = new BufferedReader(new FileReader(f));
                 line = reader.readLine();
             } catch (FileNotFoundException e) {
                 Log.d("error", ExceptionUtils.getStackTrace(e));
             } catch (IOException e) {
                 Log.d("error", ExceptionUtils.getStackTrace(e));
             } finally {
                 try {
                     if (reader != null)
                         reader.close();
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
             }
         }
         return line;
     }


    /**
     * Retrieves a JsonObject related to a document in the Local Storage in File format
     * The JsonObject retrieved is complete: it also contains the private metadata
     *
     * @param id (whether local or remote) and collection related to the document to be saved
     * @return JsonObject related to the document if it exists, null otherwise
     */

    public synchronized JsonObject _getByID(final String id, String collection) {
        if (id == null || collection == null || collection.length() == 0)
            try {
                throw new CollectionIdException();
            } catch (CollectionIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        /* if the id is a LocalUUID */
        if (id.startsWith("L")) {
            File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String s) {
                    return s.endsWith("_" + id);
                }
            });
            if (files.length == 0) return null;
            if (files.length > 1) try {
                throw new MultipleIdException();
            } catch (MultipleIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }

            JsonObject jsonObject = null;
            try {
                jsonObject = JsonObject.decode(convertFile(files[0]));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            if (jsonObject != null && jsonObject.getObject(localDataField).getString(localID) != null
                    && jsonObject.getObject(dataField).getString("@class") != null
                    && jsonObject.getObject(localDataField).getString(localID).equals(id)
                    && jsonObject.getObject(dataField).getString("@class").equals(collection))
                return jsonObject;
            else try {
                throw new CollectionIdException();
            } catch (CollectionIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        }
        /* otherwise, if the id is a RemoteUUID */
        else {
            File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String s) {
                    return s.startsWith(id + "_");
                }
            });
            if (files.length == 0) return null;
            if (files.length > 1) try {
                throw new MultipleIdException();
            } catch (MultipleIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }

            JsonObject jsonObject = null;
            try {
                jsonObject = JsonObject.decode(convertFile(files[0]));
            } catch (IOException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
            if (jsonObject != null && jsonObject.getObject(dataField).getString("id") != null
                    && jsonObject.getObject(dataField).getString("@class") != null
                    && jsonObject.getObject(dataField).getString("id").equals(id)
                    && jsonObject.getObject(dataField).getString("@class").equals(collection))
                return jsonObject;
            else try {
                throw new CollectionIdException();
            } catch (CollectionIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
        }
    }

    /**
     * Retrieves a JsonObject related to a document in the Local Storage in File format
     * The JsonObject retrieved is complete: it also contains the private metadata
     *
     * @param id (whether local or remote) related to the document to be saved
     * @return JsonObject related to the document if it exists, null otherwise
     */
    public synchronized JsonObject _getByIDWithoutCollection(final String id) {
        if (id == null)
            throw new IllegalArgumentException("the id cannot be null");
        /* If the id is a LocalUUID */
        if (id.startsWith("L")) {
            File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String s) {
                    return s.endsWith("_" + id);
                }
            });
            if (files.length == 0) return null; //oggetto non trovato
            if (files.length > 1) try {
                throw new MultipleIdException();
            } catch (MultipleIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }

            JsonObject jsonObject = null;
            try {
                jsonObject = JsonObject.decode(convertFile(files[0]));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            if (jsonObject != null && jsonObject.getObject(localDataField).getString(localID) != null
                    && jsonObject.getObject(localDataField).getString(localID).equals(id))
                return jsonObject;
            else throw new IllegalArgumentException("id not found");
        }
        /* Otherwise, if the id is a RemoteUUID */
        else {
            File[] files = this.rootDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String s) {
                    return s.startsWith(id + "_");
                }
            });
            if (files.length == 0) return null;
            if (files.length > 1) try {
                throw new MultipleIdException();
            } catch (MultipleIdException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }

            JsonObject jsonObject = null;
            try {
                jsonObject = JsonObject.decode(convertFile(files[0]));
            } catch (IOException e) {
                Log.d("error", ExceptionUtils.getStackTrace(e));
                return null;
            }
            if (jsonObject != null && jsonObject.getObject(dataField).getString("id") != null
                    && jsonObject.getObject(dataField).getString("id").equals(id))
                return jsonObject;
            else throw new IllegalArgumentException("id not found: wrong id");
        }
    }

    /**
     * Updates the "name" field of the given JsonObject if only "name" is not a reserved field
     *
     * @param json to be updated, name of the field to update, value to update
     * @return JsonObject updated if it has been updated with success
     */
    protected static JsonObject updateFieldByType(JsonObject json, String name, Object value) {
        if (value == null) {
            json.putNull(name);
        } else if (value instanceof JsonArray) {
            json.putArray(name, (JsonArray) value);
        } else if (value instanceof JsonObject) {
            json.putObject(name, (JsonObject) value);
        } else if (value instanceof String) {
            json.putString(name, (String) value);
        } else if (value instanceof Boolean) {
            json.putBoolean(name, (Boolean) value);
        } else if (value instanceof Long) {
            json.putLong(name, (Long) value);
        } else if (value instanceof Double) {
            json.putDouble(name, (Double) value);
        } else if (value instanceof byte[]) {
            json.putBinary(name, (byte[]) value);
        } else if (value instanceof JsonStructure) {
            json.putStructure(name, (JsonStructure) value);
        } else try {
            throw new InvalidFieldValueUpdateException();
        } catch (InvalidFieldValueUpdateException e) {
            Log.d("error", ExceptionUtils.getStackTrace(e));
            return null;
        }
        return json;
    }

    /**
     * Returns a JsonObject without protected fields from the JsonObject given
     *
     * @param jsonDocument to modify
     * @return JsonObject without protected fields
     */
    protected static JsonObject putLegalFieldsOnly(JsonObject jsonDocument) {
        JsonObject jsonObject = new JsonObject();
        jsonDocument.without("id");
        jsonDocument.without("@class");
        jsonDocument.without("@version");
        jsonDocument.without("_author");
        jsonDocument.without("@rid");
        jsonDocument.without("_creation_date");
        jsonDocument.without("_update_date");
        jsonDocument.without("_version");
        jsonDocument.without("_delete_date");
        jsonDocument.without("_clazz");
        Set<String> fieldSet = jsonDocument.getFieldNames();
        Iterator<String> iterator = fieldSet.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (jsonDocument.getType(key) == 1) // OBJECT
                jsonObject.putObject(key, jsonDocument.getObject(key));
            else if (jsonDocument.getType(key) == 2) //ARRAY
                jsonObject.putArray(key, jsonDocument.getArray(key));
            else if (jsonDocument.getType(key) == 3) //STRING
                jsonObject.putString(key, jsonDocument.getString(key));
            else if (jsonDocument.getType(key) == 4) //BOOLEAN
                jsonObject.putBoolean(key, jsonDocument.getBoolean(key));
            else if (jsonDocument.getType(key) == 5) //NUMBER
                if (jsonDocument.get(key) instanceof Long) // LONG
                    jsonObject.putLong(key, jsonDocument.getLong(key));
                else if (jsonDocument.get(key) instanceof Double) // DOUBLE
                    jsonObject.putDouble(key, jsonDocument.getLong(key));
                else if (jsonDocument.get(key) instanceof Byte) // Byte
                    jsonObject.putBinary(key, jsonDocument.getBinary(key));
        }
        return jsonObject;
    }

    /**
     * Checks if the given fieldName is not a reserved field
     *
     * @param fieldName
     * @return true if it is not a reserved field, false otherwise
     */
    protected static boolean checkFieldName(String fieldName) {
        return (!fieldName.equals("id") && !fieldName.equals(localID)
                && !fieldName.equals("@class") && !fieldName.equals("@version")
                && !fieldName.equals("_author") && !fieldName.equals("_dirty")
                && !fieldName.equals("_last_sync_date") && !fieldName.equals("_update_date")
                && !fieldName.equals("_version") && !fieldName.equals("_clazz") && !fieldName.equals("_delete_date")
                && !fieldName.equals("data") && !fieldName.equals("local_data"));
    }


    /**
     * Checks if the given JsonObject contains reserved fields or not
     *
     * @param jsonObject to be checked
     * @return true if it doesn't contain reserved fields, false otherwise
     */
    protected static boolean JsonWithoutReservedFields(JsonObject jsonObject) {
        Set<String> fieldSet = jsonObject.getFieldNames();
        Iterator<String> iterator = fieldSet.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!checkFieldName(key))
                return false;
        }
        return true;

    }

    /**
     * Replaces a document in the Storage with the content of a document from Server
     *
     * @param dirtyObject in the Storage, responseObjectFromServer ("data") retrieved from Server, dateFromServer (server current date)
     * @return String name of the document
     */

    protected synchronized String replaceFromServer(JsonObject dirtyObject, JsonObject responseObjectFromServer, String dateFromServer) {
        responseObjectFromServer.without("@rid");
        responseObjectFromServer.without("_creation_date");
        responseObjectFromServer.without("_update_date");
        responseObjectFromServer.without("_version");
        responseObjectFromServer.without("_delete_date");
        responseObjectFromServer.without("__clazz");
        dirtyObject.putObject(dataField, responseObjectFromServer);
        dirtyObject.getObject(localDataField).putLong("_dirty", 0);
        dirtyObject.getObject(localDataField).putString("_last_sync_date", dateFromServer);
        dirtyObject.getObject(localDataField).putString("_update_date", dateFromServer);
        return saveJson(dirtyObject);
    }


    public synchronized void setUpdateDate(String id, String dateFromServer) {
        JsonObject jsonObject = _getByIDWithoutCollection(id);
        jsonObject.getObject(localDataField).putString("_update_date", dateFromServer);
        saveJson(jsonObject);
    }

    public synchronized void setLastSyncDate(String id, String dateFromServer) {
        JsonObject jsonObject = _getByIDWithoutCollection(id);
        jsonObject.getObject(localDataField).putString("_last_sync_date", dateFromServer);
        saveJson(jsonObject);
    }

    public synchronized void setDirty(String id, Long dirty) {
        JsonObject jsonObject = _getByIDWithoutCollection(id);
        jsonObject.getObject(localDataField).putLong(("_dirty"), dirty);
        saveJson(jsonObject);
    }

    public synchronized void setVersion(String id, Long version) {
        JsonObject jsonObject = _getByIDWithoutCollection(id);
        jsonObject.getObject(dataField).putLong(("@version"), version);
        saveJson(jsonObject);
    }


    /**
     * Generate a new Date() according to the TimeZone of the oldDate (update_date)
     * It's invoked when a local update or delete operation of a document with a not null _update_date field occurs
     * to create a new Date() according to the TimeZone of the pre-existent date
     *
     * @param oldDate of the document
     * @return new Date() generated
     */

    protected static String getDateUsingTimeZone(String oldDate) {
        /* timeZone in format "+0100" */
        String timeZone = oldDate.substring(23);
        String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT" + timeZone));
        return dateFormat.format(new Date());
    }

    /**
     * Returns the corresponding String of a new Date() generated according BaasBox date format
     *
     * @return String generated
     */
    public static String generateNewDateAsString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return dateFormat.format(new Date());
    }

    /**
     * Generate the corresponding Date of the given String in BaasBox date format.
     * The given String has already to be in BaasBox date format.
     *
     * @param date to be parsed (string format)
     * @return Date parsed
     */
    public static Date generateDateFromString(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            return dateFormat.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse the given date in the BaasBox date format and returns it in String format
     *
     * @param date to be parsed
     * @return String corresponding to the date parsed
     */
    public static String generateStringFromDate(Date date) {
        if(date == null)
            return null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return dateFormat.format(date);
    }


    /**
     * Retrieve the latest _last_sync_date in the Local Storage
     *
     * @return lastSyncDate
     * *
     */
    public String latestLastSyncDate() throws IOException, ParseException {
        List<Date> dates = new ArrayList<Date>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for(File f : files) {
                    if (!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = JsonObject.decode(convertFile(f));
                        String _last_sync_date = jsonObject.getObject(localDataField).getString("_last_sync_date");
                        if (_last_sync_date != null)
                            dates.add(generateDateFromString(_last_sync_date));
                    }
                }
            } else throw new IllegalArgumentException("BaasBox directory is empty");
        } else throw new IllegalArgumentException("BaasBox directory not found");
        if (dates.size() != 0) {
            return generateStringFromDate(maxDate(dates));
        } else return null;
    }

    /**
     * Returns the maximum java.util.Date in a List of dates
     *
     * @param dateList
     * @return lastSyncDate
     */
    public static Date maxDate(List<Date> dateList) {
        Date maxSyncDate = dateList.get(0);
        for (Date time : dateList) {
            if (time.after(maxSyncDate))
                maxSyncDate = time;
        }
        return maxSyncDate;
    }

    /**
     * Convert a File into a String
     *
     *
     * @param file to be converted
     * @return string corresponding to the file
     */
    public static synchronized String convertFile(File file) throws IOException{
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    /**
     * Returns a String of all IDs (not localID) in the storage
     *
     * @return string of IDs
     * */
    public String getStringOfIDsForSync() throws IOException{
        String stringOfIDs = "[";
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if(!f.getName().equals("syncDate")){
                        JsonObject jsonObject = JsonObject.decode(convertFile(f));
                        String idServer = jsonObject.getObject("data").getString("id");
                        if (idServer != null) {
                            stringOfIDs = stringOfIDs.concat("'" + idServer + "',");
                        }
                    }
                }
            }
            stringOfIDs = stringOfIDs.concat("]");
        } else throw new IllegalArgumentException("BaasBox directory not found");
        return stringOfIDs;
    }

    /**
     * Returns a String of all collections in the storage
     *
     * @return string of collections
     * */
    public String getStringOfCollectionsForSync() throws IOException {
        String stringOfCollections = "[";
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if(!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = JsonObject.decode(convertFile(f));
                        String collection = jsonObject.getObject("data").getString("@class");
                        if (collection != null) {
                            stringOfCollections = stringOfCollections.concat("'" + collection + "',");
                        }
                    }
                }
            }
            stringOfCollections = stringOfCollections.concat("]");
        } else throw new IllegalArgumentException("BaasBox directory not found");
        return stringOfCollections;
    }

    /**
     * Returns a List of all the JsonObjects in the storage (except for the dirty created documents)
     * This List in used in the first step of synchronization algorithm
     *
     * @return List<JsonObject>
     * */
    public List<JsonObject> getListForFirstSyncStep() throws IOException {
        List<JsonObject> listForFirstSyncStep = new ArrayList<JsonObject>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if (!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = JsonObject.decode(convertFile(f));
                        String idServer = jsonObject.getObject("data").getString("id");
                        if (idServer != null) {
                            listForFirstSyncStep.add(jsonObject);
                        }
                    }
                }
            }
        } else throw new IllegalArgumentException("BaasBox directory not found");
        return listForFirstSyncStep;
    }

    /**
     * Returns a List of all the collections in the storage
     * This List in used in the first step of synchronization algorithm
     *
     * @return List<String>
     * */
    public List<String> getAllCollectionsOnTheStorage() throws IOException {
        List<String> listOfCollections = new ArrayList<String>();
        if (this.rootDirectory.exists() && this.rootDirectory.isDirectory()) {
            File[] files = this.rootDirectory.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    if(!f.getName().equals("syncDate")) {
                        JsonObject jsonObject = JsonObject.decode(convertFile(f));
                        String collection = jsonObject.getObject("data").getString("@class");
                        if (collection != null && !listOfCollections.contains(collection)) {
                            listOfCollections.add(collection);
                        }
                    }
                }
            }
        } else throw new IllegalArgumentException("BaasBox directory not found");
        return listOfCollections;
    }
}




