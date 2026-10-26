import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from klondike import geometry, persistence
from klondike.actions import DrawAction, NewGameAction, NullAction
from klondike.cards import Card, Deck, Rank, Suit
from klondike.game import Game, Layout
from klondike.strategy import AutomaticStrategy, NullStrategy


def card(suit, rank):
    return Card(Suit[suit], Rank(rank))


def layout(columns=(), stock=(), waste=(), foundations=None):
    cols = [(list(c), h) for c, h in columns]
    cols += [([], 0)] * (7 - len(cols))
    found = foundations or {}
    return Layout(cols, list(stock), list(waste), {s: list(found.get(s, [])) for s in Suit})


class DealAndCards(unittest.TestCase):
    def test_initial_deal(self):
        g = Game(seed=1)
        self.assertEqual([len(c) for c in g.columns], [1, 2, 3, 4, 5, 6, 7])
        self.assertEqual([c.hidden_count for c in g.columns], [0, 1, 2, 3, 4, 5, 6])
        self.assertEqual(len(g.stock), 24)
        self.assertTrue(g.waste.empty())
        self.assertEqual(g.score, 0)
        cards = g.all_cards()
        self.assertEqual(len(cards), 52)
        self.assertEqual(len({id(c) for c in cards}), 52)
        self.assertEqual(len({(c.suit, c.rank) for c in cards}), 52)

    def test_ranks_and_colors(self):
        self.assertTrue(Rank.SIX.is_just_below(Rank.SEVEN))
        self.assertFalse(Rank.SEVEN.is_just_below(Rank.SIX))
        self.assertTrue(Suit.HEARTS.same_color(Suit.DIAMONDS))
        self.assertFalse(Suit.HEARTS.same_color(Suit.SPADES))
        self.assertEqual(len(Deck()), 52)

    def test_shuffle_differs_between_seeds(self):
        self.assertNotEqual([str(c) for c in Game(1).stock.cards], [str(c) for c in Game(2).stock.cards])


class Rules(unittest.TestCase):
    def test_foundation_rules(self):
        g = Game(1)
        ace, two, king = card("HEARTS", 1), card("HEARTS", 2), card("HEARTS", 13)
        f = g.foundations[Suit.HEARTS]
        self.assertTrue(f.can_take((ace,)))
        self.assertFalse(f.can_take((two,)))
        self.assertFalse(f.can_take((ace, two)))
        self.assertFalse(g.foundations[Suit.CLUBS].can_take((ace,)))
        f.push(ace)
        self.assertTrue(f.can_take((two,)))
        self.assertFalse(f.can_take((king,)))

    def test_column_rules_and_waste_not_destination(self):
        g = Game(1)
        g.load_layout(layout(columns=[([card("SPADES", 7)], 0), ([], 0)], waste=[card("HEARTS", 6)]))
        six = card("HEARTS", 6)
        g.waste.replace([six])
        self.assertTrue(g.can_move(six, g.columns[0]))
        self.assertFalse(g.can_move(six, g.columns[1]))       # coluna vazia só aceita Rei
        self.assertFalse(g.can_move(six, g.waste))
        self.assertFalse(g.can_move(six, g.stock))

    def test_column_to_foundation_regression(self):
        g = Game(1)
        ace, hidden = card("HEARTS", 1), card("SPADES", 5)
        g.load_layout(layout(columns=[([hidden, ace], 1)]))
        self.assertTrue(g.can_move(ace, g.foundations[Suit.HEARTS]))
        self.assertTrue(g.move(ace, g.foundations[Suit.HEARTS]))
        self.assertEqual(g.score, 1)
        self.assertTrue(g.columns[0].is_visible(0))           # revelação automática
        g.undo()
        self.assertEqual(g.columns[0].hidden_count, 1)
        self.assertIs(g.columns[0].top(), ace)

    def test_sequence_moves_together_and_foundation_takes_one(self):
        g = Game(1)
        h8, s7, h6, s9 = card("HEARTS", 8), card("SPADES", 7), card("HEARTS", 6), card("SPADES", 9)
        g.load_layout(layout(columns=[([card("CLUBS", 3), h8, s7, h6], 1), ([s9], 0)]))
        self.assertEqual(g.sequence_from(h8), (h8, s7, h6))
        self.assertFalse(g.can_move(h8, g.foundations[Suit.HEARTS]))
        self.assertTrue(g.move(h8, g.columns[1]))
        self.assertEqual(g.columns[1].cards, (s9, h8, s7, h6))
        self.assertEqual(g.columns[0].hidden_count, 0)

    def test_hidden_cards_are_not_movable(self):
        g = Game(1)
        hidden = card("HEARTS", 1)
        g.load_layout(layout(columns=[([hidden, card("SPADES", 5)], 1)]))
        self.assertIsNone(g.locate(hidden))
        self.assertFalse(g.move(hidden, g.foundations[Suit.HEARTS]))

    def test_queries_do_not_mutate(self):
        g = Game(3)
        before = g.capture()
        for c in g.all_cards():
            g.locate(c); g.sequence_from(c); g.is_visible(c); g.is_first_visible(c)
            g.is_king_at_base(c); g.can_move(c, g.columns[0])
        self.assertEqual(before, g.capture())

    def test_king_at_base_query(self):
        g = Game(1)
        king = card("HEARTS", 13)
        g.load_layout(layout(columns=[([king], 0), ([card("CLUBS", 2), king.__class__(Suit.SPADES, Rank.KING)], 1)]))
        self.assertTrue(g.is_king_at_base(king))
        self.assertFalse(g.is_king_at_base(g.columns[1].top()))


class HistoryAndActions(unittest.TestCase):
    def test_draw_and_undo_returns_same_card(self):
        g = Game(1)
        top = g.stock.top()
        notified = []
        g.subscribe(lambda: notified.append(1))
        self.assertTrue(g.draw())
        self.assertIs(g.waste.top(), top)
        g.undo()
        self.assertIs(g.stock.top(), top)
        self.assertTrue(g.waste.empty())
        self.assertEqual(len(notified), 2)

    def test_empty_stock_starts_new_game_and_is_undoable(self):
        g = Game(1)
        g.load_layout(layout(columns=[([card("SPADES", 5)], 0)], waste=[card("HEARTS", 2)]))
        before = g.capture()
        self.assertIsInstance(g.plan_draw(), NewGameAction)
        self.assertTrue(g.draw())
        self.assertEqual(len(g.stock), 24)
        self.assertTrue(g.waste.empty())
        g.undo()
        self.assertEqual(g.capture(), before)

    def test_illegal_and_null_actions_change_nothing(self):
        g = Game(1)
        before = g.capture()
        self.assertFalse(g.perform(NullAction()))
        self.assertFalse(g.perform(NewGameAction()))          # monte não está vazio
        self.assertEqual(g.history_size, 0)
        self.assertEqual(before, g.capture())

    def test_undo_all_restores_initial_after_long_play(self):
        for seed in range(40):
            g = Game(seed)
            initial = g.capture()
            steps = 0
            while g.automatic_move():
                steps += 1
                self.assertLess(steps, 2000, "jogo automático em ciclo")
            self.assertEqual(len({id(c) for c in g.all_cards()}), 52)
            while g.undo():
                pass
            self.assertEqual(g.capture(), initial)
            self.assertTrue(g.automatic_move())               # continua jogável


class AutomaticPlay(unittest.TestCase):
    def test_decide_is_read_only_and_starts_with_draw(self):
        g = Game(7)
        before = g.capture()
        action = g.strategy.decide(g)
        self.assertIsInstance(action, DrawAction)
        self.assertEqual(before, g.capture())

    def test_priority_waste_to_foundation_over_tableau(self):
        g = Game(1)
        ace = card("HEARTS", 1)
        g.load_layout(layout(columns=[([card("SPADES", 2)], 0)], stock=[card("CLUBS", 9)], waste=[ace]))
        self.assertTrue(g.automatic_move())
        self.assertIs(g.foundations[Suit.HEARTS].top(), ace)

    def test_priority_column_to_foundation_before_tableau_move(self):
        g = Game(1)
        ace = card("HEARTS", 1)
        g.load_layout(layout(columns=[([ace], 0)], stock=[card("CLUBS", 9)], waste=[card("CLUBS", 4)]))
        self.assertTrue(g.automatic_move())
        self.assertIs(g.foundations[Suit.HEARTS].top(), ace)

    def test_king_at_base_is_not_moved_to_empty_column(self):
        g = Game(1)
        g.load_layout(layout(columns=[([card("SPADES", 13)], 0)]))
        self.assertFalse(g.automatic_move())
        self.assertEqual(g.history_size, 0)

    def test_disabled_strategy_is_uniform(self):
        g = Game(1)
        g.strategy = NullStrategy()
        self.assertFalse(g.automatic_move())
        self.assertEqual(g.history_size, 0)


class Persistence(unittest.TestCase):
    def test_round_trip_preserves_order_visibility_and_identity(self):
        g = Game(3)
        g.draw()
        text = persistence.dumps(g)
        other = Game(9)
        other.load_layout(persistence.loads(text))
        self.assertEqual(persistence.dumps(other), text)
        self.assertEqual(len({id(c) for c in other.all_cards()}), 52)
        self.assertEqual([str(c) for c in g.waste.cards], [str(c) for c in other.waste.cards])
        self.assertEqual(other.history_size, 0)

    def test_legacy_visible_from_is_accepted(self):
        text = persistence.dumps(Game(2)).replace("hidden_count", "visible_from")
        self.assertEqual(persistence.loads(text).columns[3][1], 3)

    def test_invalid_files_are_rejected(self):
        data = json.loads(persistence.dumps(Game(2)))
        broken = json.loads(json.dumps(data)); broken["stock"].pop()
        dup = json.loads(json.dumps(data)); dup["stock"][0] = dup["stock"][1]
        wrong = json.loads(json.dumps(data)); wrong["schema"] = "x"
        for bad in (broken, dup, wrong, {}, []):
            with self.assertRaises(ValueError):
                persistence.loads(json.dumps(bad))
        with self.assertRaises(ValueError):
            persistence.loads("{not json")


class Geometry(unittest.TestCase):
    def test_hit_testing_matches_drawing_for_all_columns(self):
        for j in range(7):
            self.assertEqual(geometry.column_at(geometry.column_x(j) + 10), j)
            self.assertTrue(geometry.within_column_width(geometry.column_x(j) + 10, j))
        self.assertEqual(geometry.card_index_at(geometry.card_y(2) + 5, 4), 2)
        self.assertEqual(geometry.card_index_at(geometry.card_y(3) + 90, 4), 3)
        self.assertIsNone(geometry.card_index_at(geometry.card_y(3) + 110, 4))


if __name__ == "__main__":
    unittest.main()
