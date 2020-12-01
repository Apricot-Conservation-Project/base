package main;

import mindustry.entities.Effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BaseData {


    public static final List<Effect> effectList;

    static {
        List<Effect> tList = new ArrayList<>();
        int ind = 0;
        Effect e = Effect.get(ind);
        while(e != null){
            tList.add(e);
            ind ++;
            e = Effect.get(ind);
        }
        effectList = Collections.unmodifiableList(tList);
    }
}
