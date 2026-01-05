package server.models;

public class UserStats {
    public int puzzlesPlayed;
    public int puzzlesWon;
    public int currentStreak;
    public int maxStreak;
    // Istogramma vittorie: indice 0 = 0 errori, 1 = 1 errore... 3 = 3 errori.
    // Se perdi (4 errori) non va qui, ma incrementa solo puzzlesPlayed senza puzzlesWon.
    public int[] winDistribution = new int[4]; 

    public void addWin(int errorsMade) {
        puzzlesPlayed++;
        puzzlesWon++;
        currentStreak++;
        if (currentStreak > maxStreak) {
            maxStreak = currentStreak;
        }
        // Sicurezza array bound
        if (errorsMade >= 0 && errorsMade < 4) {
            winDistribution[errorsMade]++;
        }
    }

    public void addLoss() {
        puzzlesPlayed++;
        currentStreak = 0; // Reset streak
    }
}