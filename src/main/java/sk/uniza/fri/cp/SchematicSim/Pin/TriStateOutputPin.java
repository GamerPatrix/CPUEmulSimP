package sk.uniza.fri.cp.SchematicSim.Pin;

import sk.uniza.fri.cp.SchematicSim.Electrical.PinType;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Side;

/**
 * Výstupný vývod s tromi stavmi (0, 1, Z). Na rozdiel od {@link OutputPin} (push-pull, typ OUT)
 * má typ {@link PinType#TRI_OUT}: vo vysokej impedancii sieť nebudí, takže na nej vyhrá iný
 * budič - aj slabý (napr. odbočka dátovej zbernice pri zápise CPU). Výstup typu OUT by sieť
 * v stave Z prebil hodnotou NC a vstupy na nej by čítali náhodné bity.
 */
public class TriStateOutputPin extends Pin {

    public TriStateOutputPin(GateSymbol owner, String name, int gridOffsetX, int gridOffsetY, Side side) {
        super(owner, name, Direction.OUTPUT, gridOffsetX, gridOffsetY, side);
        getOwnedPotential().setType(PinType.TRI_OUT);
    }
}
