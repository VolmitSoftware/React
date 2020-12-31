package com.volmit.react.legacy.server;

import com.volmit.react.React;
import com.volmit.react.util.A;
import primal.lang.collection.GList;

import java.io.File;
import java.util.Objects;

public class RemoteController {
    private final GList<ReactUser> users;

    public RemoteController() {
        users = new GList<>();
        reload();
    }

    public ReactUser auth(String username, String password) {
        for (ReactUser i : users) {
            if (i.getUsername().equals(username) && password.equals(i.getPassword())) {
                return i;
            }
        }

        return null;
    }

    public void reload() {
        new A() {
            @Override
            public void run() {
                users.clear();
                System.out.println("[React Server]: Loading Remote Users");
                File base = new File(React.instance().getDataFolder(), "remote-users");

                if (!base.exists()) {
                    base.mkdirs();
                }

                ReactUser m = new ReactUser("example-user");
                m.reload();

                for (File i : Objects.requireNonNull(base.listFiles())) {
                    ReactUser u = new ReactUser(i.getName().substring(0, i.getName().length() - 4));
                    u.reload();

                    if (!u.isEnabled()) {
                        continue;
                    }

                    System.out.println("[React Server]: Loaded User: " + u.getUsername());
                    users.add(u);
                }
            }
        };
    }
}
