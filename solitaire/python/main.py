"""Klondike Solitaire implementing the model and interaction requirements in the course brief."""
from __future__ import annotations
from dataclasses import dataclass
from typing import NamedTuple
from enum import Enum
import random
import json
from pathlib import Path
import tkinter as tk
from tkinter import messagebox


class Suit(Enum):
    HEARTS = ("♥", True)
    DIAMONDS = ("♦", True)
    CLUBS = ("♣", False)
    SPADES = ("♠", False)
    @property
    def red(self): return self.value[1]
    @property
    def symbol(self): return self.value[0]


@dataclass(eq=False, frozen=True)
class Card:
    suit: Suit
    rank: int
    def __str__(self): return f"{('A','2','3','4','5','6','7','8','9','10','J','Q','K')[self.rank-1]}{self.suit.symbol}"


class Pile:
    def __init__(self): self.cards: list[Card] = []
    def empty(self): return not self.cards
    def top(self): return self.cards[-1] if self.cards else None
    def push(self, card): self.cards.append(card)
    def pop(self): return self.cards.pop()


class TableauColumn(Pile):
    def __init__(self): super().__init__(); self.visible_from = 0
    def is_visible(self, index): return index >= self.visible_from
    def reveal_top(self):
        if self.cards and self.visible_from == len(self.cards): self.visible_from -= 1
    def sequence(self, index): return self.cards[index:]
    def can_take(self, cards):
        if not cards: return False
        first = cards[0]
        return first.rank == 13 if self.empty() else first.rank == self.top().rank-1 and first.suit.red != self.top().suit.red


class Location(NamedTuple):
    pile: Pile
    index: int
    sequence: list[Card] | None


@dataclass(frozen=True)
class SuggestedAction:
    kind: str
    card: Card | None = None
    destination: Pile | None = None
    to_foundation: bool = False


class AutomaticStrategy:
    """Reads the game and returns a suggestion; it never mutates game state."""
    def decide(self, game):
        if game.stock.cards and game.waste.empty(): return SuggestedAction("draw")
        waste_top=game.waste.top()
        if waste_top and game.can_found(waste_top): return SuggestedAction("move",waste_top,game.foundations[waste_top.suit],True)
        if waste_top:
            for col in game.columns:
                if game.can_move(waste_top,col): return SuggestedAction("move",waste_top,col)
        for col in game.columns:
            if col.top() and game.can_found(col.top()): return SuggestedAction("move",col.top(),game.foundations[col.top().suit],True)
        for src in game.columns:
            for i,c in enumerate(src.cards):
                if not src.is_visible(i): continue
                seq=src.sequence(i)
                if i==0 and c.rank==13: continue
                for dest in game.columns:
                    if dest is src or not dest.can_take(seq): continue
                    progress=(src.visible_from>0 and i==src.visible_from) or i==0
                    if progress: return SuggestedAction("move",c,dest)
        if game.stock.cards: return SuggestedAction("draw")
        return SuggestedAction("noop")


class Game:
    def __init__(self, seed=None): self.rng = random.Random(seed); self.listeners=[]; self.undo_stack=[]; self.strategy=AutomaticStrategy(); self.new_game()
    def subscribe(self, listener): self.listeners.append(listener)
    def notify(self):
        for fn in tuple(self.listeners): fn()
    def snapshot(self):
        return ([list(p.cards) for p in self.columns], [p.visible_from for p in self.columns], list(self.stock.cards), list(self.waste.cards), {s:list(p.cards) for s,p in self.foundations.items()})
    def to_json(self):
        """Serialize the complete position by card value; IDs restore shared identity."""
        card_id = {card: f"{card.suit.name}:{card.rank}" for pile in
                   [self.stock, self.waste, *self.columns, *self.foundations.values()] for card in pile.cards}
        payload = {"schema": "solitaire-klondike-v1",
                   "cards": [{"id": card_id[c], "suit": c.suit.name, "rank": c.rank}
                             for c in sorted(card_id, key=lambda x: (x.suit.name, x.rank))],
                   "columns": [{"cards": [card_id[c] for c in col.cards], "visible_from": col.visible_from}
                               for col in self.columns],
                   "stock": [card_id[c] for c in self.stock.cards],
                   "waste": [card_id[c] for c in self.waste.cards],
                   "foundations": {s.name: [card_id[c] for c in p.cards] for s, p in self.foundations.items()}}
        return json.dumps(payload, ensure_ascii=False, indent=2)
    def load_json(self, source):
        payload = json.loads(source)
        if payload.get("schema") != "solitaire-klondike-v1": raise ValueError("Formato de partida desconhecido")
        expected = {f"{s.name}:{r}" for s in Suit for r in range(1, 14)}
        entries=payload["cards"]
        if len(entries)!=52: raise ValueError("O baralho deve conter 52 cartas únicas")
        cards={}
        for entry in entries:
            suit=Suit[entry["suit"]]; rank=int(entry["rank"]); identifier=entry["id"]
            if not 1<=rank<=13 or identifier!=f"{suit.name}:{rank}" or identifier in cards: raise ValueError("Identidade de carta inválida ou repetida")
            cards[identifier]=Card(suit,rank)
        if set(cards) != expected: raise ValueError("O baralho deve conter as 52 cartas padrão")
        columns = payload["columns"]
        if len(columns) != 7 or set(payload["foundations"]) != {s.name for s in Suit}: raise ValueError("Estrutura da partida inválida")
        refs = [ref for group in columns for ref in group["cards"]] + payload["stock"] + payload["waste"]
        refs += [ref for group in payload["foundations"].values() for ref in group]
        if len(refs) != 52 or set(refs) != expected: raise ValueError("Cada carta deve aparecer uma única vez nas pilhas")
        self.columns = [TableauColumn() for _ in range(7)]
        for col, data in zip(self.columns, columns):
            col.cards = [cards[ref] for ref in data["cards"]]
            col.visible_from = int(data["visible_from"])
            if not 0 <= col.visible_from <= len(col.cards): raise ValueError("Visibilidade de coluna inválida")
        self.stock.cards = [cards[ref] for ref in payload["stock"]]
        self.waste.cards = [cards[ref] for ref in payload["waste"]]
        self.foundations = {s: Pile() for s in Suit}
        for s in Suit: self.foundations[s].cards = [cards[ref] for ref in payload["foundations"][s.name]]
        self.undo_stack.clear(); self.notify()
    def save(self, path): Path(path).write_text(self.to_json(), encoding="utf-8")
    def load(self, path): self.load_json(Path(path).read_text(encoding="utf-8"))
    def restore(self, snap):
        cols, vis, stock, waste, found = snap
        for p,c,v in zip(self.columns,cols,vis): p.cards=list(c); p.visible_from=v
        self.stock.cards=list(stock); self.waste.cards=list(waste)
        for s,c in found.items(): self.foundations[s].cards=list(c)
        self.notify()
    def record(self): self.undo_stack.append(self.snapshot())
    def new_game(self):
        deck=[Card(s,r) for s in Suit for r in range(1,14)]; self.rng.shuffle(deck)
        self.columns=[TableauColumn() for _ in range(7)]; self.foundations={s:Pile() for s in Suit}; self.stock=Pile(); self.waste=Pile(); self.undo_stack=[]
        for i,col in enumerate(self.columns):
            for j in range(i+1): col.push(deck.pop())
            col.visible_from=i
        for c in deck: self.stock.push(c)
        self.notify()
    @property
    def score(self): return sum(len(p.cards) for p in self.foundations.values())
    @property
    def won(self): return self.score == 52
    def draw(self):
        if not self.stock.cards:
            previous = self.snapshot()
            self.new_game()
            self.undo_stack = [previous]
            return
        self.record(); self.waste.push(self.stock.pop())
        self.notify()
    def can_found(self, card):
        p=self.foundations[card.suit]
        return card.rank == 1 if p.empty() else card.rank == p.top().rank+1
    def source_for(self, card):
        if self.waste.top() is card: return Location(self.waste, len(self.waste.cards)-1, None)
        for col in self.columns:
            for i,c in enumerate(col.cards):
                if c is card and col.is_visible(i): return Location(col,i,col.sequence(i))
        return None
    def is_bottom_king(self, card):
        return any(col.cards and col.cards[0] is card and card.rank == 13 for col in self.columns)
    def can_move(self, card, dest, foundation=False):
        src=self.source_for(card)
        if not src: return False
        pile,idx,seq=src
        if foundation: return seq is None and self.can_found(card)
        return dest is not pile and dest.can_take(seq or [card])
    def move(self, card, dest, to_foundation=False):
        if not self.can_move(card,dest,to_foundation): return False
        src=self.source_for(card); pile,index,seq=src; self.record()
        moving=(seq or [card]); del pile.cards[index:]
        if pile in self.columns and pile.empty(): pile.visible_from=0
        if pile in self.columns: pile.reveal_top()
        if to_foundation: self.foundations[card.suit].push(card)
        else:
            if dest.empty(): dest.visible_from=0
            dest.cards.extend(moving)
        self.notify(); return True
    def undo(self):
        if self.undo_stack: self.restore(self.undo_stack.pop()); return True
        return False
    def automatic_move(self):
        suggestion=self.strategy.decide(self) if self.strategy else SuggestedAction("noop")
        if suggestion.kind=="draw": self.draw(); return True
        if suggestion.kind=="move": return self.move(suggestion.card,suggestion.destination,suggestion.to_foundation)
        return False


class SolitaireUI:
    W,H,CW,CH=920,650,76,104
    def __init__(self, root):
        self.root=root; self.game=Game(); self.game.subscribe(self.render); self.canvas=tk.Canvas(root,width=self.W,height=self.H,bg="#176b46",highlightthickness=0); self.canvas.pack(fill="both",expand=True)
        self.canvas.bind("<Button-1>",self.press); self.canvas.bind("<B1-Motion>",self.drag); self.canvas.bind("<ButtonRelease-1>",self.release)
        bar=tk.Frame(root); bar.place(x=8,y=8); tk.Button(bar,text="Comprar / Nova partida",command=self.game.draw).pack(side="left"); tk.Button(bar,text="Desfazer",command=self.game.undo).pack(side="left"); tk.Button(bar,text="Jogada automática",command=self.game.automatic_move).pack(side="left"); tk.Button(bar,text="Salvar",command=self.save_game).pack(side="left"); tk.Button(bar,text="Abrir",command=self.load_game).pack(side="left")
        self.drag_card=None; self.drag_origin=None; self.drag_cards=[]; self.drag_pos=None; self.render()
    def save_game(self):
        from tkinter import filedialog
        chosen=filedialog.asksaveasfilename(defaultextension=".json",filetypes=[("Solitaire JSON","*.json")])
        if chosen:
            try:self.game.save(chosen)
            except (OSError,ValueError) as exc:messagebox.showerror("Falha ao salvar",str(exc))
    def load_game(self):
        from tkinter import filedialog
        chosen=filedialog.askopenfilename(filetypes=[("Solitaire JSON","*.json")])
        if chosen:
            try:self.game.load(chosen)
            except (OSError,ValueError,KeyError,TypeError) as exc:messagebox.showerror("Falha ao abrir",str(exc))
    def card_at(self,x,y):
        if x<90 and y<145: return None
        if 100<=x<190 and y<145: return self.game.waste.top()
        if y>=170:
            col=min(6,max(0,int(x//120)))
            p=self.game.columns[col]
            for i in range(len(p.cards)-1,-1,-1):
                yy=170+i*25
                if p.is_visible(i): yy=170+i*25
                if x<col*120+self.CW and y>=yy and (i==len(p.cards)-1 or y<170+(i+1)*25): return p.cards[i] if p.is_visible(i) else None
        return None
    def press(self,e):
        self.drag_card=self.card_at(e.x,e.y); self.drag_origin=(e.x,e.y); self.drag_cards=[]
        if self.drag_card:
            origin=self.game.source_for(self.drag_card)
            if origin and origin[2]: self.drag_cards=list(origin[2])
            else:self.drag_cards=[self.drag_card]
    def drag(self,e):
        if self.drag_cards:
            self.drag_pos=(e.x,e.y)
            self.render()
            for i,card in enumerate(self.drag_cards): self.draw_card(e.x-35,e.y+i*22,card)
    def release(self,e):
        if self.drag_origin and self.drag_origin[0]<90 and self.drag_origin[1]<145: self.game.draw()
        elif self.drag_card:
            if e.y<145:
                slot=max(0,min(3,(e.x-500)//95))
                if e.x>=500: self.game.move(self.drag_card,self.game.foundations[list(Suit)[slot]],True)
            else:
                col=min(6,max(0,int(e.x//120))); self.game.move(self.drag_card,self.game.columns[col])
        self.drag_card=None; self.drag_origin=None; self.drag_cards=[]; self.drag_pos=None; self.render()
    def render(self):
        if not hasattr(self,"canvas"): return
        c=self.canvas; c.delete("all")
        def slot(x,y,label): c.create_rectangle(x,y,x+self.CW,y+self.CH,outline="#b7d7c6",width=2); c.create_text(x+38,y+52,text=label,fill="#b7d7c6",font=("Arial",18))
        slot(10,35,"Deck" if self.game.stock.cards else "↻")
        slot(105,35,"")
        if self.game.waste.top(): self.draw_card(105,35,self.game.waste.top())
        for i,s in enumerate(Suit):
            x=500+i*95; slot(x,35,s.symbol)
            p=self.game.foundations[s]
            if p.top(): self.draw_card(x,35,p.top())
        for j,col in enumerate(self.game.columns):
            x=10+j*125; slot(x,170,"+") if not col.cards else None
            for i,card in enumerate(col.cards):
                if self.drag_cards and self.drag_card is card: continue
                origin=self.game.source_for(self.drag_card) if self.drag_card else None
                if origin and origin[0] is col and origin[1] is not None and i>=origin[1]: continue
                self.draw_card(x,170+i*25,card if col.is_visible(i) else None)
        if self.drag_card and self.drag_pos:
            px,py=self.drag_pos
            if py<145 and px>=500:
                idx=max(0,min(3,(px-500)//95)); dest=self.game.foundations[list(Suit)[idx]]; x=500+idx*95
                legal=self.game.can_move(self.drag_card,dest,True)
            elif py>=145:
                idx=max(0,min(6,int(px//125))); dest=self.game.columns[idx]; x=10+idx*125
                legal=self.game.can_move(self.drag_card,dest)
            else: dest=None; legal=False; x=0
            if dest is not None:c.create_rectangle(x,35 if py<145 else 170,x+self.CW,139 if py<145 else 274,outline="#39e36d" if legal else "#f44336",width=3)
        c.create_text(10,620,anchor="w",fill="white",text=f"Histórico: {len(self.game.undo_stack)}   {'Vitória!' if self.game.won else ''}")
    def draw_card(self,x,y,card):
        if card is None:
            self.canvas.create_rectangle(x,y,x+self.CW,y+self.CH,fill="#245a42",outline="#b7d7c6"); return
        color="#bd2438" if card.suit.red else "#17251d"
        self.canvas.create_rectangle(x,y,x+self.CW,y+self.CH,fill="#fffdf5",outline="#333")
        self.canvas.create_text(x+8,y+8,anchor="nw",text=str(card),fill=color,font=("Arial",14,"bold"))


def smoke_check():
    game=Game(seed=7)
    assert sum(len(column.cards) for column in game.columns)==28 and len(game.stock.cards)==24
    cards=[card for column in game.columns for card in column.cards]+list(game.stock.cards)
    assert len(cards)==52 and len(set(cards))==52 and game.score==0
    initial=game.snapshot(); suggestion=game.strategy.decide(game)
    assert suggestion.kind=="draw" and initial==game.snapshot()
    game.draw(); assert game.undo() and initial==game.snapshot()
    game.draw(); serialized=game.to_json(); restored=Game(seed=9); restored.load_json(serialized)
    assert json.loads(restored.to_json())==json.loads(serialized)
    all_cards=[c for p in [restored.stock,restored.waste,*restored.columns,*restored.foundations.values()] for c in p.cards]
    assert len(all_cards)==52 and len({id(c) for c in all_cards})==52
    for _ in range(23):game.draw()
    before_reset=game.snapshot();game.draw()
    assert len(game.stock.cards)==24 and game.waste.empty() and len(game.undo_stack)==1
    assert game.undo() and before_reset==game.snapshot()
    print("Solitaire Python model OK: deal, 52 unique cards, read-only strategy, draw/undo, reset undo and identity-preserving JSON save/load.")

def main():
    import sys
    if "--smoke" in sys.argv:
        smoke_check();return
    root=tk.Tk(); root.title("Solitaire — EXA871"); SolitaireUI(root); root.mainloop()
if __name__=="__main__": main()
