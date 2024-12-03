/*
 * Crea - Elimina - 30/11/2024
 */

package com.groom.manvsclass.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import com.groom.manvsclass.model.Team;
import com.groom.manvsclass.model.TeamAdmin;
import com.groom.manvsclass.model.repository.TeamAdminRepository;
import com.groom.manvsclass.model.repository.TeamRepository;

@Service
public class TeamService {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamAdminRepository teamAdminRepository;

    @Autowired
    private JwtService jwtService;  // Servizio per la validazione del JWT

    /**
     * Crea un nuovo team e associa l'Admin.
     *
     * @param team Il team da creare.
     * @param jwt Il token JWT per identificare l'Admin.
     * @return Una risposta HTTP con il risultato dell'operazione.
     */
    public ResponseEntity<?> creaTeam(Team team, @CookieValue(name = "jwt", required = false) String jwt) {

        System.out.println("Creazione del team in corso...");

        // 1. Verifica che il token JWT sia valido
        if (jwt == null || jwt.isEmpty() || !jwtService.isJwtValid(jwt)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token JWT non valido o mancante.");
        }

        // 2. Estrai l'username dell'Admin dal token JWT 
        String adminUsername = jwtService.getAdminFromJwt(jwt);
        
        if (adminUsername == null || adminUsername.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Impossibile identificare l'Admin dal token JWT.");
        }

        // 3. Controlla se il nome del team è valido
        if (team.getName() == null || team.getName().isEmpty() || team.getName().length() < 3 || team.getName().length() > 30) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nome del team non valido. Deve essere tra 3 e 30 caratteri.");
        }

        // 4. Controlla se esiste già un team con lo stesso nome
        if (teamRepository.existsByName(team.getName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Un team con questo nome esiste già.");
        }

        // 5. Aggiungi un ID univoco al team (se non specificato)
        if (team.getIdTeam() == null || team.getIdTeam().isEmpty()) {
            team.setIdTeam(generateUniqueId());
        }

        // 6. Salva il team nel database
        Team savedTeam = teamRepository.save(team);

        // 7. Crea una relazione tra Admin e Team
        TeamAdmin teamManagement = new TeamAdmin(
                adminUsername,                      // ID dell'Admin -- Ussername.
                savedTeam.getIdTeam(),        // ID del Team appena creato
                "Owner",                      // Ruolo (può essere parametrizzato)
                true                          // Relazione attiva
        );

        // 8. Salva la relazione nel database
        teamAdminRepository.save(teamManagement);

        // 9. Restituisci una risposta con il team creato
        return ResponseEntity.ok().body(savedTeam);
    }

    // Metodo per generare un ID univoco (esempio con UUID)
    private String generateUniqueId() {
        return java.util.UUID.randomUUID().toString();
    }

    
    // Elimina un team dato il nome del team
    public ResponseEntity<?> deleteTeam(String idTeam, String jwt) {
        
        // 1. Verifica se il token JWT è valido
        if (jwt == null || jwt.isEmpty() || !jwtService.isJwtValid(jwt)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token JWT non valido o mancante.");
        }
        
        // 2. Estrai l'ID dell'admin dal JWT
        String adminUsername = jwtService.getAdminFromJwt(jwt);

        System.out.print("Id da eliminare: "+idTeam);

        // 3. Verifica che il team esista
        Team teamToDelete = teamRepository.findById(idTeam).orElse(null); 
        if (teamToDelete == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Team con l'ID '" + idTeam + "' non trovato.");
        }

        // 4. Verifica che l'admin sia effettivamente associato a questo team come "Owner"
        TeamAdmin teamAdmin = teamAdminRepository.findByTeamId(idTeam); //`findByTeamId` restituisca una sola associazione
        if (teamAdmin == null || !teamAdmin.getAdminId().equals(adminUsername) || !"Owner".equals(teamAdmin.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Non hai i permessi per eliminare questo team.");
        }

        // 5. Elimina il team
        teamRepository.delete(teamToDelete);

        // 6. Elimina l'associazione
        teamAdminRepository.delete(teamAdmin);

        // Restituisci una risposta di successo
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Team con ID '" + idTeam + "' eliminato con successo.");
    }

    // Modifica il nome di un team
    public ResponseEntity<?> modificaNomeTeam(TeamModificationRequest request, @CookieValue(name = "jwt", required = false) String jwt) {
        String idTeam = request.getIdTeam();
        String newName = request.getNewName();

        // 1. Verifica se il token JWT è valido
        if (jwt == null || jwt.isEmpty() || !jwtService.isJwtValid(jwt)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token JWT non valido o mancante.");
        }

        // 2. Estrai l'ID dell'admin dal JWT
        String adminUsername = jwtService.getAdminFromJwt(jwt);

        // 3. Verifica se il team esiste
        Team existingTeam = teamRepository.findById(idTeam).orElse(null);
        if (existingTeam == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Team con l'ID '" + idTeam + "' non trovato.");
        }

        // 4. Verifica che l'admin sia effettivamente associato a questo team come "Owner"
        TeamAdmin teamAdmin = teamAdminRepository.findByTeamId(idTeam); // Assumiamo che `findByTeamId` restituisca una sola associazione
        if (teamAdmin == null || !teamAdmin.getAdminId().equals(adminUsername) || !"Owner".equals(teamAdmin.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Non hai i permessi per modificare questo team.");
        }

        // 5. Modifica il nome del team
        existingTeam.setName(newName);

        // 6. Salva il team aggiornato
        Team updatedTeam = teamRepository.save(existingTeam);

        // 7. Restituisci il team aggiornato
        return ResponseEntity.ok().body(updatedTeam);
    }

    // Metodo per visualizzare i team associati a un admin specifico
    public ResponseEntity<?> visualizzaTeams(String jwt) {
        try {
            // Estrae l'username dell'admin dal JWT
            String adminUsername = jwtService.getAdminFromJwt(jwt);

            if (adminUsername == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token non valido o scaduto.");
            }

            // Recupera i team associati a quell'admin
            List<TeamAdmin> teamAssociati = teamAdminRepository.findAllByAdminId(adminUsername);

             // Se non ci sono associazioni
            if (teamAssociati.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Nessun team associato trovato.");
            } 

            // Restituisce i team associati
            return ResponseEntity.ok().body(teamAssociati);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Errore nel recupero dei team: " + e.getMessage());
        }
    }
    
    //Modifica 02/12/2024: Aggiunta della visualizzazione del singolo team
    public ModelAndView visualizzaTeam(String idTeam,String jwt) {
        ModelAndView modelAndView = new ModelAndView();

        // Verifica se il token JWT è presente
        if (jwt == null || jwt.isEmpty()) {
            modelAndView.setViewName("login"); // Reindirizza alla pagina di login
            return modelAndView;
        }

        // Recupera il team dal database
        Team team = teamRepository.getTeamById(idTeam);

        // Gestione del caso in cui il team non viene trovato
        if (team == null) {
            modelAndView.setViewName("error"); // Mostra una pagina di errore
            modelAndView.addObject("message", "Il team con ID " + idTeam + " non è stato trovato.");
            return modelAndView;
        }

        // Configura la view con il nome e i dati
        modelAndView.setViewName("teamDetail"); // Nome del template
        modelAndView.addObject("team", team);  

        return modelAndView;
    }

}

