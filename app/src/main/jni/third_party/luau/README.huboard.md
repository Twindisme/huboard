# Vendored Luau

huBoard vendors the runtime, compiler, AST, bytecode, and common components from Luau
0.736 (`c2ec0d4e5ca50796ba174a7565298f59aa572268`).

Source archive:
`https://github.com/luau-lang/luau/archive/refs/tags/0.736.tar.gz`

SHA-256:
`e80f61e402500bf155f9fb260fc4a8f6ec8b7fb2e471b115b7e22111e993da86`

Only the components needed to compile and execute sandboxed theme scripts are
included. Luau and its Lua-derived portions retain their upstream MIT license;
see `LICENSE.txt` and `lua_LICENSE.txt`.
