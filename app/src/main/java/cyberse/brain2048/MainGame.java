package cyberse.brain2048;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainGame {

    public static final int CHECK_RIGHT_ANIMATION = -2;
    public static final int SPAWN_ANIMATION = -1;
    public static final int MOVE_ANIMATION = 0;
    public static final int MERGE_ANIMATION = 1;
    public static final int CHECK_WRONG_ANIMATION = 2;

    public static final int FADE_GLOBAL_ANIMATION = 0;
    private static final long MOVE_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME;
    private static final long SPAWN_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME;
    private static final long NOTIFICATION_DELAY_TIME = MOVE_ANIMATION_TIME + SPAWN_ANIMATION_TIME;
    private static final long NOTIFICATION_ANIMATION_TIME = MainView.BASE_ANIMATION_TIME * 5;
    private static  final int MAX_MOVE = 100;
    private static final int startingMaxValue = 2048;
    //Odd state = game is not active
    //Even state = game is active
    //Win state = active state + 1
    private static final int GAME_WIN = 1;
    private static final int GAME_LOST = -1;
    private static final int GAME_NORMAL = 0;
    private static final int GAME_SHOW = 3;
    public int gameState = GAME_NORMAL;
    public int lastGameState = GAME_NORMAL;
    private int bufferGameState = GAME_NORMAL;
    private static final String HIGH_SCORE = "high score";
    private static int endingMaxValue;
    final int numSquaresX = 4;
    final int numSquaresY = 4;
    private final Context mContext;
    private final MainView mView;
    public Grid grid = null;
    public Grid winGrid = null;
    public Grid tempGrid = null;
    public AnimationGrid aGrid;
    public boolean canUndo;
    public long score = 0;
    public long highScore = 0;
    public long lastScore = 0;
    private long bufferScore = 0;

    public MainGame(Context context, MainView view) {
        mContext = context;
        mView = view;
        endingMaxValue = view.numCellTypes - 1;
    }

    public void newGame() {
        if (grid == null) {
            grid = new Grid(numSquaresX, numSquaresY);
        } else {
            prepareUndoState();
            saveUndoState();
            grid.clearGrid();
        }
        if(winGrid == null){
            winGrid = new Grid(numSquaresX, numSquaresY);
        }
        //animation
        aGrid = new AnimationGrid(numSquaresX, numSquaresY);
        highScore = getHighScore();
        if (score >= highScore) {
            highScore = score;
            recordHighScore();
        }
        score = 0;
        gameState = GAME_NORMAL;
        addStartTiles();
        if(tempGrid == null){
            tempGrid = new Grid(numSquaresX, numSquaresY);
        }
        copyGridState(tempGrid, grid);
        makeWinningState();
        highScore = score;
        copyGridState(winGrid, grid);
        gameState = GAME_SHOW;
        aGrid.cancelAnimations();
        spawnGridAnimation();
        mView.refreshLastTime = true;
        mView.resyncTime();
        mView.invalidate();
    }

    private void addStartTiles() {;
        for (int xx = 0; xx < grid.field.length; xx++) {
            int ignoreCellY = (int)(Math.random() * grid.field[0].length);
            for(int yy = 0; yy < grid.field[0].length; yy++)
            {
                if(yy != ignoreCellY)
                {
                    this.addTile(xx, yy);
                }
            }

        }
    }

    private void addTile(int x, int y)
    {
        if(grid.field[x][y] == null)
        {
            int value = Math.random() <= 0.5 ? 1 : Math.random() <= 0.6 ? 2 : 3;
            Tile tile = new Tile(new Cell(x, y), value);
            spawnTile(tile);
        }
    }


    private void spawnTile(Tile tile) {
        grid.insertTile(tile);
        aGrid.startAnimation(tile.getX(), tile.getY(), SPAWN_ANIMATION,
                SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null); //Direction: -1 = EXPANDING
    }

    private void recordHighScore() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(HIGH_SCORE, highScore);
        editor.commit();
    }

    private long getHighScore() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        return settings.getLong(HIGH_SCORE, -1);
    }

    private void prepareTiles() {
        for (Tile[] array : grid.field) {
            for (Tile tile : array) {
                if (grid.isCellOccupied(tile)) {
                    tile.setMergedFrom(null);
                }
            }
        }
    }

    private void moveTile(Tile tile, Cell cell) {
        grid.field[tile.getX()][tile.getY()] = null;
        grid.field[cell.getX()][cell.getY()] = tile;
        tile.updatePosition(cell);
    }

    private void saveUndoState() {
        grid.saveTiles();
        canUndo = true;
        lastScore = bufferScore;
        lastGameState = bufferGameState;
    }

    public void gameStart()
    {
        if(gameShow()){
            copyGridState(grid, tempGrid);
            gameState = GAME_NORMAL;
            grid.clearUndoGrid();
            canUndo = false;
            score = 0;

            aGrid.cancelAnimations();
            spawnGridAnimation();

            mView.refreshLastTime = true;
            mView.resyncTime();
            mView.invalidate();
        } else if (!isActive()){
            Toast.makeText(mContext, R.string.game_not_start_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void makeWinningState()
    {
        int loop = 0;
        while(grid.countOccupiedCell() > numSquaresX + 1 && loop <= MAX_MOVE){
            move((int)(Math.random() * 4));
            loop++;
        }
    }

    private void spawnGridAnimation(){
        for (int xx = 0; xx < grid.field.length; xx++) {
            for (int yy = 0; yy < grid.field[0].length; yy++) {
                if(grid.field[xx][yy] != null){
                    aGrid.startAnimation(xx, yy, SPAWN_ANIMATION,
                            SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null); //Direction: -1 = EXPANDING
                }
            }
        }
    }

    private void copyGridState(Grid target, Grid source)
    {
        for (int xx = 0; xx < source.field.length; xx++) {
            for (int yy = 0; yy < source.field[0].length; yy++) {
                if(source.field[xx][yy] == null){
                    target.field[xx][yy] = null;
                } else {
                    target.field[xx][yy] = new Tile(xx, yy, source.field[xx][yy].getValue());
                }
            }
        }
    }

    private void prepareUndoState() {
        grid.prepareSaveTiles();
        bufferScore = score;
        bufferGameState = gameState;
    }

    public void revertUndoState() {
        if(!isActive()){
            return;
        }
        if (canUndo) {
            canUndo = false;
            aGrid.cancelAnimations();
            grid.revertTiles();
            score = lastScore;
            gameState = lastGameState;
            mView.refreshLastTime = true;
            mView.invalidate();
        }
    }

    public boolean gameWon() {
        return (gameState > 0 && gameState % 2 != 0);
    }

    public boolean gameLost() {
        return (gameState == GAME_LOST);
    }

    public boolean gameShow() {return gameState == GAME_SHOW;}

    public boolean isActive() {
        return !(gameWon() || gameLost() || gameShow());
    }

    public void move(int direction) {
        aGrid.cancelAnimations();
        // 0: up, 1: right, 2: down, 3: left
        if (!isActive()) {
            return;
        }
        prepareUndoState();
        Cell vector = getVector(direction);
        List<Integer> traversalsX = buildTraversalsX(vector);
        List<Integer> traversalsY = buildTraversalsY(vector);
        boolean moved = false;

        prepareTiles();

        for (int xx : traversalsX) {
            for (int yy : traversalsY) {
                Cell cell = new Cell(xx, yy);
                Tile tile = grid.getCellContent(cell);

                if (tile != null) {
                    Cell[] positions = findFarthestPosition(cell, vector);
                    Tile next = grid.getCellContent(positions[1]);
                    if (next != null  && next.getValue() == tile.getValue() && next.getMergedFrom() == null) {
                        Tile merged = new Tile(positions[1], tile.getValue() + 1);
                        Tile[] temp = {tile, next};
                        merged.setMergedFrom(temp);

                        grid.insertTile(merged);
                        grid.removeTile(tile);

                        // Converge the two tiles' positions
                        tile.updatePosition(positions[1]);

                        int[] extras = {xx, yy};
                        aGrid.startAnimation(merged.getX(), merged.getY(), MOVE_ANIMATION,
                                MOVE_ANIMATION_TIME, 0, extras); //Direction: 0 = MOVING MERGED
                        aGrid.startAnimation(merged.getX(), merged.getY(), MERGE_ANIMATION,
                                SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null);

                        // Update the score
                        score = score + merged.getValue();
                    } else {
                        moveTile(tile, positions[0]);
                        int[] extras = {xx, yy, 0};
                        aGrid.startAnimation(positions[0].getX(), positions[0].getY(), MOVE_ANIMATION, MOVE_ANIMATION_TIME, 0, extras); //Direction: 1 = MOVING NO MERGE
                    }

                    if (!positionsEqual(cell, tile)) {
                        moved = true;
                    }
                }
            }
        }

        if (moved) {
            saveUndoState();
            //addRandomTile();
            checkWin();
            checkLose();
        }
        mView.resyncTime();
        mView.invalidate();
    }

    public void move(int xx, int yy, int direction) {
        aGrid.cancelAnimations();
        // 0: up, 1: right, 2: down, 3: left
        Log.d("move x:", Integer.toString(xx));
        Log.d("move y:", Integer.toString(yy));
        if (!isActive()) {
            return;
        }
        prepareUndoState();
        Cell vector = getVector(direction);
        boolean moved = false;

        prepareTiles();


        Cell cell = new Cell(xx, yy);
        Tile tile = grid.getCellContent(cell);

        if (tile != null) {
            Cell[] positions = findFarthestPosition(cell, vector);
            Tile next = grid.getCellContent(positions[1]);
            if (next != null  && next.getValue() == tile.getValue() && next.getMergedFrom() == null) {
                Tile merged = new Tile(positions[1], tile.getValue() + 1);
                Tile[] temp = {tile, next};
                merged.setMergedFrom(temp);

                grid.insertTile(merged);
                grid.removeTile(tile);

                // Converge the two tiles' positions
                tile.updatePosition(positions[1]);

                int[] extras = {xx, yy};
                aGrid.startAnimation(merged.getX(), merged.getY(), MOVE_ANIMATION,
                        MOVE_ANIMATION_TIME, 0, extras); //Direction: 0 = MOVING MERGED
                aGrid.startAnimation(merged.getX(), merged.getY(), MERGE_ANIMATION,
                        SPAWN_ANIMATION_TIME, MOVE_ANIMATION_TIME, null);

                // Update the score
                score = score + merged.getValue();

            } else {
                moveTile(tile, positions[0]);
                int[] extras = {xx, yy, 0};
                aGrid.startAnimation(positions[0].getX(), positions[0].getY(), MOVE_ANIMATION, MOVE_ANIMATION_TIME, 0, extras); //Direction: 1 = MOVING NO MERGE
            }

            if (!positionsEqual(cell, tile)) {
                moved = true;
            }
        }

        if (moved) {
            saveUndoState();
            //addRandomTile();
            checkWin();
            checkLose();
        }
        mView.resyncTime();
        mView.invalidate();
    }
    private void checkLose() {
        if(grid.countOccupiedCell() < winGrid.countOccupiedCell() - 1){
            gameState = GAME_LOST;
            endGame();
        }
    }

    private  void checkWin(){
        if(isWin()){
            gameState = gameState + GAME_WIN; // Set win state
            endGame();
        }
    }

    private boolean isWin() {
        for(int xx = 0; xx < grid.field.length; xx++)
        {
            for(int yy = 0; yy < grid.field[0].length; yy++)
            {
                if(grid.field[xx][yy] == null && winGrid.field[xx][yy] == null)
                    continue;
                if(grid.field[xx][yy] == null && winGrid.field[xx][yy] != null)
                    return false;
                else if(grid.field[xx][yy] != null && winGrid.field[xx][yy] == null)
                    return false;
                else if(grid.field[xx][yy].getValue() != winGrid.field[xx][yy].getValue())
                    return false;
            }
        }
        return true;
    }

    public void checkWinState(){
        for(int xx = 0; xx < grid.field.length; xx++)
        {
            for(int yy = 0; yy < grid.field[0].length; yy++)
            {
                if(grid.field[xx][yy] != null  ){
                    if(winGrid.field[xx][yy] != null && grid.field[xx][yy].getValue() == winGrid.field[xx][yy].getValue()){
                        aGrid.startAnimation(xx, yy, CHECK_RIGHT_ANIMATION,
                                NOTIFICATION_ANIMATION_TIME, 0, null);
                    } else {
//                        aGrid.startAnimation(xx, yy, CHECK_WRONG_ANIMATION,
//                                NOTIFICATION_ANIMATION_TIME, 0, null);
                    }
                }
            }
        }
        mView.refreshLastTime = false;
        mView.resyncTime();
        mView.invalidate();
    }

    private void endGame() {
        aGrid.startAnimation(-1, -1, FADE_GLOBAL_ANIMATION, NOTIFICATION_ANIMATION_TIME, NOTIFICATION_DELAY_TIME, null);
        if (score >= highScore) {
            highScore = score;
            recordHighScore();
        }
    }

    private Cell getVector(int direction) {
        Cell[] map = {
                new Cell(0, -1), // up
                new Cell(1, 0),  // right
                new Cell(0, 1),  // down
                new Cell(-1, 0)  // left
        };
        return map[direction];
    }

    private List<Integer> buildTraversalsX(Cell vector) {
        List<Integer> traversals = new ArrayList<>();

        for (int xx = 0; xx < numSquaresX; xx++) {
            traversals.add(xx);
        }
        if (vector.getX() == 1) {
            Collections.reverse(traversals);
        }

        return traversals;
    }

    private List<Integer> buildTraversalsY(Cell vector) {
        List<Integer> traversals = new ArrayList<>();

        for (int xx = 0; xx < numSquaresY; xx++) {
            traversals.add(xx);
        }
        if (vector.getY() == 1) {
            Collections.reverse(traversals);
        }

        return traversals;
    }

    private Cell[] findFarthestPosition(Cell cell, Cell vector) {
        Cell previous;
        Cell nextCell = new Cell(cell.getX(), cell.getY());
        do {
            previous = nextCell;
            nextCell = new Cell(previous.getX() + vector.getX(),
                    previous.getY() + vector.getY());
        } while (grid.isCellWithinBounds(nextCell) && grid.isCellAvailable(nextCell));

        return new Cell[]{previous, nextCell};
    }


    private boolean positionsEqual(Cell first, Cell second) {
        return first.getX() == second.getX() && first.getY() == second.getY();
    }


}
