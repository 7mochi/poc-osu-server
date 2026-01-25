package pe.nanamochi.banchus.utils;

import pe.nanamochi.banchus.entities.Privileges;

import java.util.EnumSet;
import java.util.Set;

public class PrivilegesUtil {

    PrivilegesUtil() {}

    public static int toMask(Set<Privileges> set) {
        int mask = 0;
        for (Privileges p : set) {
            mask |= p.bitPosition();
        }
        return mask;
    }

    public static Set<Privileges> fromMask(int mask) {
        EnumSet<Privileges> set = EnumSet.noneOf(Privileges.class);
        for (Privileges p : Privileges.values()) {
            if ((mask & p.bitPosition()) != 0) {
                set.add(p);
            }
        }
        return set;
    }

    public static boolean has(int mask, int privilege) {
        Set<Privileges> privilegesSet = fromMask(privilege);
        return privilegesSet.stream().allMatch(p -> has(mask, p));
    }

    public static boolean has(int mask, Privileges privilege) {
        return (mask & privilege.bitPosition()) != 0;
    }

    public static int add(int mask, Privileges privilege) {
        return mask | privilege.bitPosition();
    }

    public static int remove(int mask, Privileges privilege) {
        return mask & ~privilege.bitPosition();
    }
}
