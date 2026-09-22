package it.visiovoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Il servizio digitale su cui si lavora.
 *
 * @param fidelity che rapporto ha la pagina che stiamo usando col portale vero. Va dichiarato
 *                 perche' una demo su una replica che si presenta come il portale vero e' la
 *                 stessa disonesta' che il gate di fidelity esiste per prevenire
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceInfo(
        String name,
        String authority,
        String url,
        ServiceFidelity fidelity) {

    public ServiceInfo {
        fidelity = fidelity == null ? ServiceFidelity.REPLICA : fidelity;
    }
}
