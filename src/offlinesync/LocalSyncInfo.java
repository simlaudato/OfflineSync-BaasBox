package offlinesync;

/**
 * Created by Simone on 16/07/2014.
 */

/**
 * Classe che gestisce i metadati LOCALI necessari per la risoluzione di un conflitto
 *
 * */

public class LocalSyncInfo {


    private String localID;
    private int dirty;
    private String last_sync_date;
    private String update_date;
    private Long version;

    // -------------------------- CONSTRUCTOR --------------------------

    public LocalSyncInfo(String localID, int dirty, String LAST_SYNC_DATE, String UPDATE_DATE, Long version) {
        this.localID = localID;
        this.dirty = dirty;
        this.last_sync_date = LAST_SYNC_DATE;
        this.update_date = UPDATE_DATE;
        this.version = version;
    }

    // -------------------------- GETTER & SETTER --------------------------


    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getLocalID() {
        return localID;
    }

    public void setLocalID(String localID) {
        this.localID = localID;
    }

    public int getDirty() {
        return dirty;
    }

    public void setDirty(int dirty){
        this.dirty = dirty;
    }

    public String getLast_sync_date() {
        return last_sync_date;
    }

    public void setLast_sync_date(String last_sync_date) {
        this.last_sync_date = last_sync_date;
    }

    public String getUpdate_date() {
        return update_date;
    }

    public void setUpdate_date(String update_date) {
        this.update_date = update_date;
    }
}
