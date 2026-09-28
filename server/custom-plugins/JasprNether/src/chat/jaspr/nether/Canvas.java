package chat.jaspr.nether;

/**
 * Anything the mega structures and wonders draw on: the live population {@link Area} or the offline preview volume.
 * Values are combined (id << 4 | meta); reads outside the canvas return bedrock so nothing is placed against the unknown.
 */
interface Canvas {
    int get(int x, int y, int z);
    void set(int x, int y, int z, int combined);
}
