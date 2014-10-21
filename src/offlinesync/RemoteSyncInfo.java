package offlinesync;

/**
 * Created by Simone on 16/07/2014.
 */

/**
 * Classe che gestisce i metadati REMOTI necessari per la risoluzione di un conflitto
 *
 * */

public class RemoteSyncInfo {


    private String id;
    private String deletion_date;
    private String update_date;
    private Long version;

    // -------------------------- CONSTRUCTOR --------------------------

    public RemoteSyncInfo(String id, String deletion_date, String update_date, Long version){
        this.id = id;
        this.deletion_date = deletion_date;
        this.update_date = update_date;
        this.version = version;
    }

    // -------------------------- GETTER & SETTER --------------------------


    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeletion_date() {
        return deletion_date;
    }

    public void setDeletion_date(String deletion_date) {
        this.deletion_date = deletion_date;
    }

    public String getUpdate_date() {
        return update_date;
    }

    public void setUpdate_date(String update_date) {
        this.update_date = update_date;
    }
}
