package aabase;

import arc.struct.Array;
import mindustry.content.Fx;
import mindustry.entities.Effects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BaseData {


    public static final List<Effects.Effect> effectList;
    public static final List<String> effectNames;

    static {
        List<Effects.Effect> tList = new ArrayList<>();
        List<String> nList = new ArrayList<>();
        for (Field f : Fx.class.getDeclaredFields()) {
            try {
                Object value = f.get((new Fx()));
                tList.add((Effects.Effect) value);
                nList.add(f.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        effectList = Collections.unmodifiableList(tList);
        effectNames = Collections.unmodifiableList(nList);
    }
}
