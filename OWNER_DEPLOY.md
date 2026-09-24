# Putting updates live (for the owner)

## The short version
1. Claude finishes a change and opens a pull request on GitHub.
2. You merge it on GitHub.
3. On the PC, open the JasperCraft folder and double-click **Deploy JasperCraft.bat**.
4. Read the list of changes it shows, type **Y** and press Enter.
5. Wait until it says **Done! The update is live.** Then press any key to close the window.

It only runs when you double-click it. Nothing runs by itself in the background.

## What the window does
- Downloads the new version into a separate folder (`Documents\jaspercraft-deploy`). Your live folder is not touched yet.
- If there is nothing new it says **Already up to date** and stops.
- Tries new plugins on a small test server first. Players do not notice this.
- If people are playing, it **waits** until nobody is online (it never kicks anyone). Press any key to cancel the wait.
- Makes a backup, puts the new files in place and restarts the game server (1-3 minutes). Website-only changes need no restart.
- Checks that everything started. **If anything fails it puts the old version back by itself** and tells you.

## If it stops or something looks wrong
- Every message says in plain words what happened. "Nothing was changed" means the live server is exactly as before.
- To go back to the version before the last update, double-click **Undo last deploy.bat** and type **Y**.
- Tell Claude. It can read the result on GitHub (the `deploy-status` branch) and the full log is in
  `.runtime\deploy\deploy.log` in the JasperCraft folder.
- If the deployer says a change "needs a Claude review first", open a Claude session on the PC and ask it to review
  and install the new deployer. This is a safety rule: the deployer never updates itself.
