package com.toeic.backend.assessment.application;

import com.toeic.backend.questionbank.application.PracticeQuestionCatalog.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** Bounded subset sum per Part, followed by exact allocation across Parts. No greedy false shortage. */
@Component
public class PracticeUnitSelector {
    private record Path(Unit unit,Path previous) { }
    private record Allocation(int part,int amount,Allocation previous) { }
    public List<Pin> select(List<Unit> units,PracticeStartQuery query) { return select(units,query,ThreadLocalRandom.current()); }
    List<Pin> select(List<Unit> units,PracticeStartQuery query,RandomGenerator random) {
        int target=query.questionCount();
        var perPart=new LinkedHashMap<Integer,Path[]>();
        for(int part:query.partNumbers()) {
            var candidates=new ArrayList<>(units.stream().filter(u->u.partNumber()==part&&!u.questions().isEmpty()).toList());
            shuffle(candidates,random);
            var paths=new Path[target+1];paths[0]=new Path(null,null);
            for(var unit:candidates) {
                int weight=unit.questions().size();
                for(int n=target;n>=weight;n--)if(paths[n]==null&&paths[n-weight]!=null)paths[n]=new Path(unit,paths[n-weight]);
            }
            perPart.put(part,paths);
        }
        var allocations=new Allocation[target+1];allocations[0]=new Allocation(0,0,null);
        for(var entry:perPart.entrySet()) {
            var next=new Allocation[target+1];var amounts=new ArrayList<Integer>();
            for(int n=1;n<=target;n++)if(entry.getValue()[n]!=null)amounts.add(n);
            shuffle(amounts,random);
            for(int prior=0;prior<target;prior++)if(allocations[prior]!=null)for(int amount:amounts)
                if(prior+amount<=target&&next[prior+amount]==null)next[prior+amount]=new Allocation(entry.getKey(),amount,allocations[prior]);
            allocations=next;
        }
        if(allocations[target]==null)throw PracticeException.insufficient();
        var chosen=new TreeMap<Integer,List<Unit>>();
        for(var a=allocations[target];a.previous()!=null;a=a.previous()) {
            var selected=new ArrayList<Unit>();
            for(var p=perPart.get(a.part())[a.amount()];p.unit()!=null;p=p.previous())selected.add(p.unit());
            shuffle(selected,random);chosen.put(a.part(),selected);
        }
        var result=chosen.values().stream().flatMap(List::stream).flatMap(u->u.questions().stream()).toList();
        if(result.size()!=target||result.stream().map(Pin::questionId).distinct().count()!=target)throw PracticeException.insufficient();
        return result;
    }
    private static <T> void shuffle(List<T> values,RandomGenerator random) {
        for(int i=values.size()-1;i>0;i--)Collections.swap(values,i,random.nextInt(i+1));
    }
}
