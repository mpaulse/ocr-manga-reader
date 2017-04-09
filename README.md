A customized version of OCR Manga Reader that allows for greater control in the
manner in which Anki cards are created by supporting card types (notes) with more
than 2 fields (not just front and back).

Card types are currently assumed to have exactly 4 fields available to acommodate
the following information in the order given below:

* Japanese expression
* Reading
* English definition
* Example sentence (currently not used)

Support for configurable field-to-info mappings might be implemented in future.

Based on OCR Manga Reader 6.1 (http://ocrmangareaderforandroid.sourceforge.net/).

Requires a fairly recent version of AnkiDroid to be installed (>= 2.5).
When using AnkiDroid < 2.5, the app will fallback to using the previous card
creation functionality, where cards are created as follows:

* Front: Japanese expression + [Reading]
* Back: English definition

Only tablets supported at this stage.

