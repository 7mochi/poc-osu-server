package pe.nanamochi.banchus.entities;

import pe.nanamochi.banchus.utils.PrivilegesUtil;

import java.util.Set;

public enum Privileges {

    // privileges intended for all normal players.
    UNRESTRICTED(0), // is an unbanned player.
    VERIFIED(1), // has logged in to the server in-game.

    // has bypass to low-ceiling anticheat measures (trusted).
    WHITELISTED(2), //

    // donation tiers, receives some extra benefits.
    SUPPORTER(4), //
    PREMIUM(5), //

    // notable users, receives some extra benefits.
    ALUMNI(7), //

    // staff permissions, able to manage server app.state.
    TOURNEY_MANAGER(10), // able to manage match state without host.
    NOMINATOR(11), // able to manage maps ranked status.
    MODERATOR(12), // able to manage users (level 1).
    ADMINISTRATOR(13), // able to manage users (level 2).
    DEVELOPER(14); // able to manage full server app.state.

    private final int bitPosition;

    Privileges(int bitPosition) {
        this.bitPosition = bitPosition;
    }

    public int bitPosition() {
        return bitPosition;
    }

    public static final int DONATOR_MASK = PrivilegesUtil.toMask(Set.of(SUPPORTER, PREMIUM));
    public static final int STAFF_MASK = PrivilegesUtil.toMask(Set.of(MODERATOR, ADMINISTRATOR, DEVELOPER));
}
