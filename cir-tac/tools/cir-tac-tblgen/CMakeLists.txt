set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-rtti")

set(LIBS
  MLIRTblgenLib
)

add_executable(cir-tac-tblgen
  cir-tac-tblgen.cpp
  Common.cpp
  AttrGen.cpp
  EnumGen.cpp
  GenHelpers.cpp
  OpGen.cpp
  TypeGen.cpp
)

target_link_libraries(cir-tac-tblgen ${LIBS})
