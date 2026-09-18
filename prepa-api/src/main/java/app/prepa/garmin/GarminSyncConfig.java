package app.prepa.garmin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Le fil d'execution des synchronisations lancees a la demande.
 *
 * <p>Un passage dure des minutes : quatre appels a Garmin par seance, avec une respiration entre
 * chacun. Le faire dans le fil de la requete HTTP exposerait l'appelant a un delai d'attente
 * expire au milieu du travail — l'ingestion continuerait sans que personne ne sache ou elle en
 * est.
 *
 * <p>Un seul fil, et une file courte : le runner pose de toute facon un verrou global, et
 * accumuler des demandes qui attendront leur tour derriere ce verrou ne fait qu'eloigner le
 * moment ou l'on saura qu'elles etaient inutiles. Au-dela, la demande est rejetee dans le fil
 * appelant, ce qui la transforme en refus immediat plutot qu'en attente muette.
 */
@Configuration
public class GarminSyncConfig {

    public static final String EXECUTEUR = "garminSyncExecutor";

    @Bean(EXECUTEUR)
    public ThreadPoolTaskExecutor garminSyncExecutor() {
        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(1);
        executeur.setMaxPoolSize(1);
        executeur.setQueueCapacity(8);
        executeur.setThreadNamePrefix("garmin-sync-");
        executeur.setWaitForTasksToCompleteOnShutdown(true);
        executeur.setAwaitTerminationSeconds(30);
        return executeur;
    }
}
