package server.models;

public class UserStats {
    public int puzzlesPlayed;
    public int puzzlesWon;
    public int currentStreak;
    public int maxStreak;
    
    // NUOVO CAMPO: Fondamentale per la classifica (Ranking)
    public int totalScore; 

    // Istogramma risultati:
    // Indici 0-3: Vittorie con 0, 1, 2, 3 errori
    // Indice 4: Sconfitte (Tempo scaduto o Max errori) - Serve alla UI per la barra rossa
    public int[] winDistribution = new int[5]; 

    public UserStats() {
        // Costruttore vuoto necessario per GSON
    }

    /**
     * Registra una vittoria
     * @param errorsMade Numero di errori commessi (0-3)
     */
    public void addWin(int errorsMade) {
        // Nota: puzzlesPlayed viene incrementato dal chiamante o qui? 
        // Solitamente lo incrementiamo qui per sicurezza.
        // Se il UserManager lo incrementa già, rimuovi questa riga.
        // puzzlesPlayed++; 
        
        puzzlesWon++;
        currentStreak++;
        if (currentStreak > maxStreak) {
            maxStreak = currentStreak;
        }

        // Aggiorna istogramma vittorie
        if (errorsMade >= 0 && errorsMade < 4) {
            winDistribution[errorsMade]++;
        }
    }

    /**
     * Registra una sconfitta
     */
    public void addLoss() {
        // puzzlesPlayed++; // Vedi nota sopra
        currentStreak = 0; // Reset streak
        
        // Registra la sconfitta nell'istogramma (indice 4)
        if (winDistribution.length > 4) {
            winDistribution[4]++;
        }
    }
}