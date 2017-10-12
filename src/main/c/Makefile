# 
# RecSplit for C.
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

TARGETS = rec-split
CC = gcc
CFLAGS = -Wall -g -O3

all: $(TARGETS)

rec-split: rec-split.o

#	gcc rec-split.o -o rec-split

rec-split.o: rec-split.c

#	gcc -c rec-split.c -o rec-split.o

clean:
	rm -f *.a *.o $(TARGETS)

decompile:
	objdump -S --disassemble rec-split