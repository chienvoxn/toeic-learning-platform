package com.toeic.backend.assessment.application;

import com.toeic.backend.questionbank.application.PracticeQuestionCatalog.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeUnitSelectorTest {
    private final PracticeUnitSelector selector=new PracticeUnitSelector();
    private Unit unit(int part,int count) {
        var pins=new ArrayList<Pin>();boolean grouped=Set.of(3,4,6,7).contains(part);
        UUID group=grouped?UUID.randomUUID():null,version=grouped?UUID.randomUUID():null;
        for(int i=0;i<count;i++)pins.add(new Pin(UUID.randomUUID(),UUID.randomUUID(),group,version,part));
        return new Unit(part,pins);
    }
    @Test void findsExactCombinationInsteadOfGreedilyTakingFourAndFailingSix() {
        var units=List.of(unit(3,4),unit(3,3),unit(3,3));
        for(int seed=0;seed<30;seed++) {
            var selected=selector.select(units,new PracticeStartQuery(List.of(3),6,null,null),new Random(seed));
            assertEquals(6,selected.size());assertEquals(2,selected.stream().map(Pin::groupVersionId).distinct().count());
            assertTrue(selected.stream().noneMatch(units.getFirst().questions()::contains));
        }
    }
    @Test void everyRequestedPartIsPresentAndPartsAndGroupMembersKeepOrder() {
        var units=List.of(unit(1,1),unit(1,1),unit(3,4),unit(3,3),unit(3,3),unit(7,2));
        var result=selector.select(units,new PracticeStartQuery(List.of(7,1,3),9,null,null),new Random(3));
        assertEquals(9,result.size());assertEquals(List.of(1,3,7),result.stream().map(Pin::partNumber).distinct().toList());
        for(var u:units)if(result.contains(u.questions().getFirst())) {
            int start=result.indexOf(u.questions().getFirst());
            assertEquals(u.questions(),result.subList(start,start+u.questions().size()));
        }
        assertEquals(result.size(),result.stream().map(Pin::questionId).distinct().count());
    }
    @Test void rejectsImpossibleTotalsAndMissingRequestedParts() {
        var units=List.of(unit(3,3),unit(3,3));
        assertEquals("PRACTICE_QUESTIONS_INSUFFICIENT",assertThrows(PracticeException.class,
                ()->selector.select(units,new PracticeStartQuery(List.of(3),5,null,null))).getCode());
        assertThrows(PracticeException.class,()->selector.select(units,new PracticeStartQuery(List.of(1,3),6,null,null)));
    }
    @Test void feasibilityMatchesExhaustiveSubsetsIncludingPartCoverage() {
        var units=List.of(unit(1,1),unit(1,1),unit(3,2),unit(3,3),unit(3,4),unit(4,2),unit(4,3));
        for(int count=1;count<=16;count++) {
            boolean possible=false;
            for(int bits=1;bits<(1<<units.size());bits++) {
                int sum=0;var parts=new HashSet<Integer>();
                for(int n=0;n<units.size();n++)if((bits&(1<<n))!=0){sum+=units.get(n).questions().size();parts.add(units.get(n).partNumber());}
                if(sum==count&&parts.equals(Set.of(1,3,4))){possible=true;break;}
            }
            var query=new PracticeStartQuery(List.of(1,3,4),count,null,null);
            if(possible)assertEquals(count,selector.select(units,query,new Random(count)).size());
            else assertThrows(PracticeException.class,()->selector.select(units,query,new Random(1)));
        }
    }
    @Test void independentUnitsAreRandomizedWithoutShufflingChoicesOrGroupMembers() {
        var units=new ArrayList<Unit>();for(int n=0;n<20;n++)units.add(unit(1,1));
        var query=new PracticeStartQuery(List.of(1),5,null,null);
        var a=selector.select(units,query,new Random(1));var b=selector.select(units,query,new Random(2));
        assertNotEquals(a,b);assertEquals(5,a.size());assertEquals(5,b.size());
    }
    @Test void maximumTwoHundredQuestionsIsReachableWithoutDuplicates() {
        var units=new ArrayList<Unit>();for(int n=0;n<200;n++)units.add(unit(1,1));
        var result=selector.select(units,new PracticeStartQuery(List.of(1),200,null,null),new Random(7));
        assertEquals(200,result.size());assertEquals(200,result.stream().map(Pin::questionId).distinct().count());
    }
}
